package com.gmail.alexei28.shortcut.kafka.consumer.task3;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gmail.alexei28.shortcut.kafka.consumer.task3.consumer.OrderEventConsumer;
import com.gmail.alexei28.shortcut.kafka.consumer.task3.repo.AccountOperationRepository;
import com.gmail.alexei28.shortcut.kafka.consumer.task3.repo.ProcessedMessageRepository;
import com.gmail.alexei28.shortcut.kafka.consumer.task3.service.PaymentService;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.util.StreamUtils;
import org.springframework.util.backoff.FixedBackOff;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

@ActiveProfiles("test")
@SpringBootTest(
    properties = {
      "spring.kafka.consumer.auto-offset-reset=earliest",
      "spring.kafka.listener.ack-mode=manual"
    })
@Testcontainers
class IdempotentConsumerIntegrationTest {
  @Value("${app.kafka.topics.task3}")
  private String topic;

  @Value("${app.kafka.groups.task3}")
  private String consumerGroup;

  @Container @ServiceConnection
  static KafkaContainer kafkaContainer =
      new KafkaContainer(DockerImageName.parse("apache/kafka-native:4.1.1"));

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgresContainer =
      new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

  // overrideProps вызывается один раз при создании Spring ApplicationContext.
  // Все тесты класса будут работать с одной уникальной группой.
  @DynamicPropertySource
  static void overrideProps(DynamicPropertyRegistry registry) {
    // Берем префикс из проперти
    String prefix = "test-task3-group";
    registry.add("app.kafka.groups.task3", () -> prefix + "-" + UUID.randomUUID());
  }

  @TestConfiguration
  static class LocalTestConfig {
    @Bean
    public DefaultErrorHandler errorHandler() {
      /*
        Полностью отключает retry Spring Kafka:
         - retryInterval = 0 ms
         - maxRetries = 0
         Если не отключить retry, то при падении метода processOrderCreation (например, если он выбросит RuntimeException),
         Spring Kafka будет бесконечно пытаться обработать это сообщение снова, что приводит к зависанию теста.
      */
      return new DefaultErrorHandler(new FixedBackOff(0L, 0));
    }
  }

  @Autowired private KafkaTemplate<String, String> kafkaTemplate;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private AccountOperationRepository accountOperationRepository;
  @Autowired private ProcessedMessageRepository processedMessageRepository;
  /*
      Поскольку OrderEventConsumer помечен как @MockitoSpyBean, Spring использует реальный экземпляр,
      но позволяет нам «подсматривать» за его методами через verify.
      @MockitoSpyBean: Позволяет нам следить за реальным бином OrderEventConsumer и считать количество вызовов метода consume.
      Spring создает настоящий экземпляр вашего OrderEventConsumer со всеми его зависимостями (repository, taskMapper).
      Обертка (Spy): Mockito «оборачивает» этот реальный объект.
      Это позволяет вам:
      -Вызывать реальные методы (код внутри consume и process будет выполнен).
      -Следить за вызовами (использовать verify, чтобы посчитать количество вызовов).
      -Переопределять поведение только конкретных методов, если нужно (через doThrow или doReturn).
  */
  @MockitoSpyBean private OrderEventConsumer orderEventConsumerMock;
  @MockitoSpyBean private PaymentService paymentServiceMock;
  private String createOrderRequestValidJson;
  private static String jsonTemplate;
  private JsonNode createOrderRequestValidJsonRoot;

  @BeforeAll
  static void beforeAll() throws IOException {
    jsonTemplate =
        StreamUtils.copyToString(
            new ClassPathResource("create_order_event_template.json").getInputStream(),
            StandardCharsets.UTF_8);
  }

  @BeforeEach
  void setUp() throws JsonProcessingException {
    // Чистим БД перед каждым тестом, чтобы не было пересечений данных между тестами.
    accountOperationRepository.deleteAll();
    processedMessageRepository.deleteAll();

    // Update specific nodes in the JSON
    DocumentContext context =
        JsonPath.parse(jsonTemplate)
            .set("$.eventId", UUID.randomUUID().toString())
            .set("$.externalId", UUID.randomUUID().toString());
    createOrderRequestValidJson = context.jsonString();
    createOrderRequestValidJsonRoot = objectMapper.readTree(createOrderRequestValidJson);
  }

  /*
    Сообщение обрабатывается и сохраняется операция
  */
  @Test
  void shouldProcessMessageAndPersistOperation() throws Exception {
    // Act
    kafkaTemplate.send(
        topic,
        createOrderRequestValidJsonRoot.get("eventId").asText(),
        createOrderRequestValidJson);

    // Assert
    await()
        .atMost(Duration.ofSeconds(20))
        .untilAsserted(
            () -> {
              assertThat(processedMessageRepository.count()).isEqualTo(1);
              assertThat(accountOperationRepository.count()).isEqualTo(1);
            });

    verify(orderEventConsumerMock, times(1)).consume(any(), any(), any(), any(), any());
  }

  /*
    ДУБЛИКАТ (at-least-once) -> Idempotent Consumer.
    Дубликат не приводит к повторной бизнес-операции
  */

  @Test
  void shouldIgnoreDuplicateMessage() throws Exception {
    // Act
    // отправляем 2 одинаковых события
    kafkaTemplate.send(
        topic,
        createOrderRequestValidJsonRoot.get("eventId").asText(),
        createOrderRequestValidJson);
    kafkaTemplate.send(
        topic,
        createOrderRequestValidJsonRoot.get("eventId").asText(),
        createOrderRequestValidJson);

    // Assert
    await()
        .atMost(Duration.ofSeconds(20))
        .untilAsserted(
            () -> {
              assertThat(processedMessageRepository.count()).isEqualTo(1);
              assertThat(accountOperationRepository.count()).isEqualTo(1);
            });

    // consumer вызовется 2 раза (Kafka доставит оба сообщения)
    verify(orderEventConsumerMock, times(2)).consume(any(), any(), any(), any(), any());
  }

  /*
    При ошибке ACK не вызывается и сообщение будет переобработано.
    Если processOrderCreation бросает исключение -> ack.acknowledge() не вызывается
  */

  @Test
  void shouldNotAckAndNotCommitOffsetWhenExceptionThrown() {
    // Arrange
    // Тест отправляет сообщение. Оно падает. Spring Kafka начинает его ретраить (retry).
    // Spring Kafka перехватывает исключение и, следуя стандартной политике DefaultErrorHandler,
    // начинает бесконечно (или многократно) пытаться обработать это сообщение снова.
    doThrow(new RuntimeException("Some my custom exception"))
        .when(paymentServiceMock)
        .processOrderCreation(any(), any());

    // Act
    kafkaTemplate.send(
        topic,
        createOrderRequestValidJsonRoot.get("eventId").asText(),
        createOrderRequestValidJson);

    // Assert
    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () -> {
              // consumer вызовется 1 раз
              verify(orderEventConsumerMock, times(1)).consume(any(), any(), any(), any(), any());
              // Ничего не сохранится
              assertThat(processedMessageRepository.count()).isZero();
              assertThat(accountOperationRepository.count()).isZero();
            });
  }
}
