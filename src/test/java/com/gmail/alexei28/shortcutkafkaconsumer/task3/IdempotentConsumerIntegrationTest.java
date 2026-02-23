package com.gmail.alexei28.shortcutkafkaconsumer.task3;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gmail.alexei28.shortcutkafkaconsumer.task3.consumer.OrderEventConsumer;
import com.gmail.alexei28.shortcutkafkaconsumer.task3.repo.AccountOperationRepository;
import com.gmail.alexei28.shortcutkafkaconsumer.task3.repo.ProcessedMessageRepository;
import com.gmail.alexei28.shortcutkafkaconsumer.task3.service.PaymentService;
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
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.io.ClassPathResource;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.util.StreamUtils;
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

  @Autowired private KafkaTemplate<String, String> kafkaTemplate;
  @Autowired private ObjectMapper objectMapper;

  @Autowired private AccountOperationRepository accountOperationRepository;
  @Autowired private ProcessedMessageRepository processedMessageRepository;

  @MockitoSpyBean private OrderEventConsumer orderEventConsumerSpy;
  @MockitoSpyBean private PaymentService paymentService;
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
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () -> {
              assertThat(processedMessageRepository.count()).isEqualTo(1);
              assertThat(accountOperationRepository.count()).isEqualTo(1);
            });

    verify(orderEventConsumerSpy, times(1)).consume(any(), any(), any());
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
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () -> {
              assertThat(processedMessageRepository.count()).isEqualTo(1);
              assertThat(accountOperationRepository.count()).isEqualTo(1);
            });

    // consumer вызовется 2 раза (Kafka доставит оба сообщения)
    verify(orderEventConsumerSpy, times(2)).consume(any(), any(), any());
  }

  /*
    При ошибке ACK не вызывается и сообщение будет переобработано.
    Если processOrderCreation бросает исключение -> ack.acknowledge() не вызывается
  */
  @Test
  void shouldNotAckAndNotCommitOffsetWhenExceptionThrown() {
    // Arrange
    // заставляем сервис падать
    doThrow(new RuntimeException("boom")).when(paymentService).processOrderCreation(any(), any());

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
              verify(orderEventConsumerSpy, times(1)).consume(any(), any(), any());
              // Ничего не сохранится
              assertThat(processedMessageRepository.count()).isZero();
              assertThat(accountOperationRepository.count()).isZero();
            });
  }
}
