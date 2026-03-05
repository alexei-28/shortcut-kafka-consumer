package com.gmail.alexei28.shortcutkafkaconsumer.task5;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gmail.alexei28.shortcutkafkaconsumer.task5.dto.UserDto;
import com.gmail.alexei28.shortcutkafkaconsumer.task5.entity.DlqMessage;
import com.gmail.alexei28.shortcutkafkaconsumer.task5.entity.DlqStatus;
import com.gmail.alexei28.shortcutkafkaconsumer.task5.repo.DlqMessageRepository;
import com.gmail.alexei28.shortcutkafkaconsumer.task5.repo.UserRepository;
import com.gmail.alexei28.shortcutkafkaconsumer.task5.service.DlqRetrySchedulerService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.util.StreamUtils;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class RetryIntegrationTest {
  /*
     Testcontainers поднимает контейнеры в фоне, а Spring Boot через @ServiceConnection автоматически использует
     их для бинов KafkaTemplate и DataSource.
     Переменные контейнеров нужны только для конфигурации, даже если не вызывать из вручную.
     - Нигде в тесте не пишем kafkaContainer.getBootstrapServers() или postgresContainer.getJdbcUrl().
     - Это не нужно, потому что Spring Boot и Testcontainers делают это за нас через @ServiceConnection.
     - Контейнеры нужны лишь для запуска сервисов и предоставления адресов, остальное Spring делает автоматически.
  */
  @Container @ServiceConnection
  static KafkaContainer kafkaContainer =
      new KafkaContainer(DockerImageName.parse("apache/kafka-native:4.1.1"));

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgresContainer =
      new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

  @Value("${app.kafka.topics.task5}")
  private String topic;

  // overrideProps вызывается один раз при создании Spring ApplicationContext.
  // Все тесты класса будут работать с одной уникальной группой.
  @DynamicPropertySource
  static void overrideProps(DynamicPropertyRegistry registry) {
    // Берем префикс из проперти
    String prefix = "test-task5-group";
    registry.add("app.kafka.groups.task5", () -> prefix + "-" + UUID.randomUUID());
  }

  @Value("${app.kafka.retry.max-attempts}")
  private int maxAttempts;

  /*
      Поскольку KafkaTemplate помечен как @MockitoSpyBean, Spring использует реальный экземпляр,
      но позволяет нам «подсматривать» за его методами через verify.
      @MockitoSpyBean: Позволяет нам следить за реальным бином KafkaTemplate.
      Spring создает настоящий экземпляр вашего KafkaTemplate со всеми его зависимостями (repository, taskMapper).
      Обертка (Spy): Mockito «оборачивает» этот реальный объект.
      Это позволяет вам:
      -Вызывать реальные методы (код внутри consume и process будет выполнен).
      -Следить за вызовами (использовать verify, чтобы посчитать количество вызовов).
      -Переопределять поведение только конкретных методов, если нужно (через doThrow или doReturn).
  */
  @MockitoSpyBean private KafkaTemplate<String, UserDto> kafkaTemplateMock;
  @Autowired private TestRestTemplate testRestTemplate;
  @Autowired private DlqMessageRepository dlqMessageRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private ObjectMapper objectMapper;
  private static String jsonTemplate;
  private String eventId;
  private UserDto userDto;
  private DlqMessage dlqMessage;
  private static final int INIT_RETRY_COUNT = 0;
  @Autowired private DlqRetrySchedulerService dlqRetrySchedulerService;

  @TestConfiguration
  static class KafkaTestConfig {
    @Bean
    public KafkaTemplate<String, UserDto> kafkaTemplate(ProducerFactory<String, UserDto> pf) {
      return new KafkaTemplate<>(pf);
    }
  }

  @BeforeAll
  static void beforeAll() throws IOException {
    jsonTemplate =
        StreamUtils.copyToString(
            new ClassPathResource("create_user_template.json").getInputStream(),
            StandardCharsets.UTF_8);
  }

  @BeforeEach
  void setUp() throws JsonProcessingException {
    // Чистим БД перед каждым тестом, чтобы не было пересечений данных между тестами.
    userRepository.deleteAll();
    dlqMessageRepository.deleteAll();

    eventId = UUID.randomUUID().toString();
    userDto = objectMapper.readValue(jsonTemplate, UserDto.class);
    dlqMessage = new DlqMessage();
    dlqMessage.setPayload(objectMapper.writeValueAsString(userDto));
    dlqMessage.setTopic(topic);
    dlqMessage.setStatus(DlqStatus.NEW);
    dlqMessage.setRetryCount(INIT_RETRY_COUNT);
  }

  @Test
  @DisplayName(
      "Should increment retryCount 3 times when KafkaTemplate fails with TimeoutException by manual retry")
  void shouldHandleTimeoutExceptionDuringManualRetry() {
    // Arrange
    dlqMessage.setEventId(eventId);
    dlqMessageRepository.save(dlqMessage);

    // "Заламываем" метод send: при любом вызове выбрасываем TimeoutException
    // Используем doThrow, так как это Spy
    doThrow(new org.apache.kafka.common.errors.TimeoutException("Kafka timeout simulated"))
        .when(kafkaTemplateMock)
        .send(anyString(), anyString(), any(UserDto.class));

    // Act & Assert: Имитируем 3 вызова ручного ретрая через ваш сервис/контроллер
    for (int i = 1; i <= maxAttempts; i++) {
      final int expectedCount = i;

      // Вызываем контроллер (как в вашем тесте manual retry)
      testRestTemplate.postForEntity("/dlq/retry/{key}", null, String.class, eventId);

      // Проверяем, что в базе retryCount увеличился, несмотря на ошибку отправки
      await()
          .atMost(Duration.ofSeconds(5))
          .pollInterval(Duration.ofMillis(300))
          .untilAsserted(
              () -> {
                DlqMessage dlqMessageUpdated =
                    dlqMessageRepository.findByEventId(eventId).orElseThrow();
                assertThat(dlqMessageUpdated.getRetryCount()).isEqualTo(expectedCount);
                assertThat(dlqMessageUpdated.getStatus()).isEqualTo(DlqStatus.FAILED);
                assertThat(dlqMessageUpdated.getErrorClass()).contains("TimeoutException");
              });
    }

    // После 3 попыток статус должен быть FAILED, а не RETRIED, так как мы имитируем постоянную
    // ошибку
    testRestTemplate.postForEntity("/dlq/retry/{key}", null, String.class, eventId);

    // Проверяем, что после 3 попыток статус стал EXHAUSTED и retryCount не увеличился
    await()
        .atMost(Duration.ofSeconds(5))
        .pollInterval(Duration.ofMillis(300))
        .untilAsserted(
            () -> {
              DlqMessage dlqMessageUpdated =
                  dlqMessageRepository.findByEventId(eventId).orElseThrow();
              assertThat(dlqMessageUpdated.getRetryCount()).isEqualTo(maxAttempts);
              assertThat(dlqMessageUpdated.getStatus()).isEqualTo(DlqStatus.EXHAUSTED);
              assertThat(dlqMessageUpdated.getErrorMessage()).isEqualTo("Max attempts reached");
              assertThat(dlqMessageUpdated.getErrorClass()).contains("TimeoutException");
            });
  }

  @Test
  @DisplayName("Scheduler should retry message until maxAttempts and mark as EXHAUSTED")
  void shouldHandleTimeoutExceptionDuringSchedulerRetry() {
    // Arrange
    dlqMessage.setEventId(eventId);
    dlqMessageRepository.save(dlqMessage);

    doThrow(new org.apache.kafka.common.errors.TimeoutException("Kafka timeout simulated"))
        .when(kafkaTemplateMock)
        .send(anyString(), anyString(), any(UserDto.class));

    // ---- 3 попытки retry ----
    for (int i = 1; i <= maxAttempts; i++) {
      final int expectedRetry = i;
      // Act
      dlqRetrySchedulerService.retryMessages();

      // Assert
      await()
          .atMost(Duration.ofSeconds(5))
          .pollInterval(Duration.ofMillis(300))
          .untilAsserted(
              () -> {
                DlqMessage updated = dlqMessageRepository.findByEventId(eventId).orElseThrow();
                assertThat(updated.getRetryCount()).isEqualTo(expectedRetry);
                assertThat(updated.getStatus()).isEqualTo(DlqStatus.FAILED);
                assertThat(updated.getErrorClass()).contains("TimeoutException");
              });
    }

    // ---- 4 вызов scheduler → EXHAUSTED ----
    dlqRetrySchedulerService.retryMessages();
    await()
        .atMost(Duration.ofSeconds(5))
        .pollInterval(Duration.ofMillis(300))
        .untilAsserted(
            () -> {
              DlqMessage updated = dlqMessageRepository.findByEventId(eventId).orElseThrow();
              assertThat(updated.getRetryCount()).isEqualTo(maxAttempts);
              assertThat(updated.getStatus()).isEqualTo(DlqStatus.EXHAUSTED);
              assertThat(updated.getErrorMessage()).isEqualTo("Max attempts reached");
              assertThat(updated.getErrorClass()).contains("TimeoutException");
            });
  }
}
