package com.gmail.alexei28.shortcut.kafka.consumer.task5;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gmail.alexei28.shortcut.kafka.consumer.task5.dto.UserDto;
import com.gmail.alexei28.shortcut.kafka.consumer.task5.entity.DlqMessage;
import com.gmail.alexei28.shortcut.kafka.consumer.task5.entity.DlqStatus;
import com.gmail.alexei28.shortcut.kafka.consumer.task5.entity.User;
import com.gmail.alexei28.shortcut.kafka.consumer.task5.repo.DlqMessageRepository;
import com.gmail.alexei28.shortcut.kafka.consumer.task5.repo.UserRepository;
import com.gmail.alexei28.shortcut.kafka.consumer.task5.service.DlqRetrySchedulerService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.StreamUtils;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class DlqIntegrationTest {
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

  /*
     Важно: Генерируем уникальный groupId для каждого теста, чтобы избежать проблем с offset-ами при повторных запусках тестов.
     Как уникальный groupId решает проблему.
     Когда для каждого теста мы генерируем уникальный groupId
     То:
         1. Kafka создаёт новую группу для консьюмера.
         2. Offset для этой группы всегда начинается с нуля.
         3. Все сообщения из топика будут прочитаны заново, независимо от предыдущих тестов.
         4. Тесты становятся изолированными — они не влияют друг на друга.
  */
  @DynamicPropertySource
  static void overrideProps(DynamicPropertyRegistry registry) {
    // Берем префикс из проперти
    String prefix = "test-task5-group";
    registry.add("app.kafka.groups.task5", () -> prefix + "-" + UUID.randomUUID());
  }

  @Value("${app.kafka.retry.max-attempts}")
  private int maxAttempts;

  @Autowired private KafkaTemplate<String, UserDto> kafkaTemplate;
  @Autowired private TestRestTemplate testRestTemplate;
  @Autowired private DlqMessageRepository dlqMessageRepository;
  @Autowired private DlqRetrySchedulerService dlqRetrySchedulerService;
  @Autowired private UserRepository userRepository;
  @Autowired private ObjectMapper objectMapper;
  private static String jsonTemplate;
  private static final String INVALID_INN = "ABC123";
  private static final String INVALID_EMAIL = "invalid-email";
  @Autowired private KafkaTemplate<String, String> kafkaStringTemplate;
  private String eventId;
  private UserDto userDto;
  private DlqMessage dlqMessage;
  private static final int INIT_RETRY_COUNT = 0;
  private static final String BROKEN_JSON =
      """
                             {
                              "userId": "123",
                              "firstName": "Alexei",
                              "lastName": "Ivanov",
                              "email": "alexei@test.com",
                              "inn": 12345   // <-- нет кавычек + оборван JSON
                            """;

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

  /*
     Тест проверяет:
     - Невалидное сообщение -> запись в таблицу DLQ.
     - Маршрутизация ошибки: Что DefaultErrorHandler успешно перехватил DeserializationException.
     - Сохранность данных: Что «битый» payload не потерялся и записан в БД именно в том виде, в котором пришел.
     - Метаданные: Что код в KafkaConsumerConfig#errorHandler корректно извлек topic, partition и offset из consumerRecord,
       даже если само тело сообщения не удалось распарсить.
  */
  @Test
  @DisplayName("Should save message to DLT table when invalid JSON is received")
  void shouldSaveToDltWhenJsonIsInvalid() {
    // Act
    // Используем StringTemplate, чтобы отправить сырую строку в обход десериализатора на стороне
    // продюсера.
    // Должно упасть именно на десериализации в консьюмере, и DefaultErrorHandler должен перехватить
    // это.
    // KafkaConsumerConfig#errorHandler должен быть вызван.
    kafkaStringTemplate.send(topic, eventId, BROKEN_JSON);

    // Assert
    await()
        .atMost(Duration.ofSeconds(10))
        .pollInterval(Duration.ofMillis(500))
        .untilAsserted(
            () -> {
              Optional<DlqMessage> dltMessageOpt = dlqMessageRepository.findByEventId(eventId);
              assertThat(dltMessageOpt).isPresent();
              DlqMessage msg = dltMessageOpt.get();
              assertAll(
                  () ->
                      assertThat(msg.getPayload())
                          .contains("userId")
                          .contains("Alexei")
                          .contains("inn"),
                  () -> assertThat(msg.getTopic()).isEqualTo(topic),
                  () -> assertThat(msg.getStatus()).isEqualTo(DlqStatus.NEW),
                  () -> assertThat(msg.getErrorClass()).contains("Exception"),
                  // Проверяем, что метаданные (partition/offset) заполнились
                  () -> assertThat(msg.getPartitionNumber()).isNotNull(),
                  () -> assertThat(msg.getOffsetValue()).isNotNull());
            });
  }

  /*
    Тест проверяет:
   - Невалидное сообщение -> запись в таблицу DLQ.
   - Прохождение десериализации: В отличие от первого теста с brokenJson, здесь используется kafkaTemplate.send(..., UserDto).
     Jackson создаст валидный JSON, и консьюмер успешно превратит его в объект.
   - Бизнес-ошибка: Метод saveUser вызовет validate(userDto), который увидит "ABC123" и выбросит IllegalArgumentException.
   - Перехват: Spring Kafka перехватит это исключение.
     Если CommonErrorHandler настроен на запись в БД при любых ошибках (включая бизнес-логику),
     запись в dlqMessageRepository появится автоматически.
  */
  @Test
  @DisplayName("Should save to DLQ when INN contains non-digits")
  void shouldSaveToDlqWhenInnHasInvalidFormat() {
    // Arrange
    // Подготавливаем DTO с буквами в ИНН (нарушает паттерн ^\\d{1,20}$)
    userDto.setInn(INVALID_INN);

    // Act
    // Отправляем корректный JSON (через KafkaTemplate<String, UserDto>), чтобы упасть именно на
    // бизнес-валидации, а не на десериализации.
    // KafkaConsumerConfig#errorHandler должен быть вызван.
    kafkaTemplate.send(topic, eventId, userDto);

    // Assert
    await()
        .atMost(Duration.ofSeconds(10))
        .pollInterval(Duration.ofMillis(500))
        .untilAsserted(
            () -> {
              Optional<DlqMessage> dlqMessageOpt = dlqMessageRepository.findByEventId(eventId);

              assertThat(dlqMessageOpt).isPresent();

              DlqMessage msg = dlqMessageOpt.get();
              assertAll(
                  () -> assertThat(msg.getPayload()).contains(INVALID_INN),
                  () -> assertThat(msg.getTopic()).isEqualTo(topic),
                  () -> assertThat(msg.getStatus()).isEqualTo(DlqStatus.NEW),
                  // Проверяем, что пойман именно IllegalArgumentException из UserService
                  () -> assertThat(msg.getErrorClass()).contains("IllegalArgumentException"),
                  // Проверяем сообщение из вашего validate() метода
                  () ->
                      assertThat(msg.getErrorMessage())
                          .contains("INN must contain 1-20 digits only"),
                  // Базовые проверки метаданных Kafka
                  () -> assertThat(msg.getPartitionNumber()).isGreaterThanOrEqualTo(0),
                  () -> assertThat(msg.getOffsetValue()).isGreaterThanOrEqualTo(0L));
            });
  }

  /*
    Невалидное сообщение -> запись в таблицу DLQ.
  */
  @Test
  @DisplayName("Should save to DLQ when Email format is invalid")
  void shouldSaveToDlqWhenEmailIsInvalid() {
    userDto.setEmail(INVALID_EMAIL);

    // Act
    // Отправляем корректный JSON (через KafkaTemplate<String, UserDto>), чтобы упасть именно на
    // бизнес-валидации, а не на десериализации.
    // KafkaConsumerConfig#errorHandler должен быть вызван.
    kafkaTemplate.send(topic, eventId, userDto);

    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () -> {
              Optional<DlqMessage> dlqMessageOpt = dlqMessageRepository.findByEventId(eventId);
              assertThat(dlqMessageOpt).isPresent();

              DlqMessage msg = dlqMessageOpt.get();
              assertAll(
                  () -> assertThat(msg.getErrorClass()).contains("IllegalArgumentException"),
                  () -> assertThat(msg.getErrorMessage()).isEqualTo("Invalid email format"),
                  () -> assertThat(msg.getPayload()).contains(INVALID_EMAIL));
            });
  }

  @Test
  @DisplayName("Should mark message as EXHAUSTED after max retries")
  void shouldMarkAsExhaustedAfterMaxRetries() {
    // Arrange
    dlqMessage.setEventId(eventId);
    dlqMessage.setStatus(DlqStatus.FAILED);
    // Create a message already at the limit
    dlqMessage.setRetryCount(maxAttempts);
    dlqMessageRepository.save(dlqMessage);
    // Проверяем, что сообщение сохранено в DLQ с статусом FAILED
    assertThat(dlqMessageRepository.findByStatusAndTopic(DlqStatus.FAILED, topic)).hasSize(1);

    // Act
    dlqRetrySchedulerService.retryMessages();

    // Assert
    await()
        .atMost(Duration.ofSeconds(15))
        .pollInterval(Duration.ofMillis(500))
        .untilAsserted(
            () -> {
              DlqMessage updatedMsg =
                  dlqMessageRepository
                      .findByEventId(eventId)
                      .orElseThrow(() -> new AssertionError("Message disappeared from DB"));

              assertThat(updatedMsg.getStatus())
                  .as("After " + maxAttempts + " retries, status should be EXHAUSTED")
                  .isEqualTo(DlqStatus.EXHAUSTED);
            });
  }

  /*
     Тест проверяет ручной retry через REST API:
      - Подготовка: Сначала мы сохраняем DlqMessage в статусе FAILED(TimeoutException),
        имитируя ситуацию, когда сообщение только что попало в таблицу task5_dlq_messages.
      - Вызов API: Затем мы вызываем REST API для ручного успешного ретрая (сообщение уходит в главный топи).
      - Ожидание:
           UserConsumer#consumer успешно обработает сообщение из главного топика, сохранит User в БД и вызовет dlqMessageUpdater.deleteIfExists(eventId).
           Сообщение будет удалено из таблицы task5_dlq_messages и мы будем ждать, пока консьюмер не обработает его.
           Сообщение должно появиться в главном топике и в таблице task5_users.
  */
  @Test
  @DisplayName("Manual retry via REST API should process message and delete from DLQ")
  void shouldProcessDlqMessageOnManualRetry() {
    // Arrange
    dlqMessage.setEventId(eventId);
    dlqMessage.setStatus(DlqStatus.FAILED);
    dlqMessage.setErrorClass("org.apache.kafka.common.errors.TimeoutException");
    dlqMessageRepository.save(dlqMessage);
    // Проверяем, что сообщение сохранено в DLQ с статусом FAILED
    assertThat(dlqMessageRepository.findByStatusAndTopic(DlqStatus.FAILED, topic)).hasSize(1);

    // Act
    // REST API должен запустить retry, который отправит сообщение в главный топик.
    // Консьюмер должен его обработать,
    ResponseEntity<String> response =
        testRestTemplate.postForEntity("/dlq/retry/{key}", null, String.class, eventId);

    // Assert HTTP response
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).contains("Retry triggered for messages, key = " + eventId);

    // Проверка, что message был добавлен в таблицу task5_users. Consumer успешно обработал message
    await()
        .atMost(Duration.ofSeconds(10))
        .pollInterval(Duration.ofMillis(500))
        .untilAsserted(
            () -> {
              Optional<User> userOpt = userRepository.findByEventId(eventId);
              assertThat(userOpt).isPresent();
              User savedUser = userOpt.get();
              assertThat(savedUser.getFirstName()).isEqualTo(userDto.getFirstName());
              assertThat(savedUser.getLastName()).isEqualTo(userDto.getLastName());
              assertThat(savedUser.getEmail()).isEqualTo(userDto.getEmail());
            });

    // Проверяем, что запись удалена
    await()
        .atMost(Duration.ofSeconds(10))
        .pollInterval(Duration.ofMillis(200))
        .untilAsserted(
            () -> {
              Optional<DlqMessage> deletedDlq = dlqMessageRepository.findByEventId(eventId);
              assertThat(deletedDlq).isEmpty();
            });
  }

  /*
     Тест проверяет ручной retry через REST API:
      - Подготовка: Сначала мы сохраняем DlqMessage в статусе FAILED (JsonProcessingException),
        имитируя ситуацию, когда сообщение только что попало в таблицу task5_dlq_messages.
      - Вызов API: Затем мы вызываем REST API для ручного неуспешного ретрая (JsonProcessingException).
      - Ожидание:
         В таблице task5_dlq_messages статус должен измениться на EXHAUSTED.
  */
  @Test
  @DisplayName("Manual fail retry via REST API should set status to EXHAUSTED")
  void shouldSetStatusExhaustedWhenFailedManualRetry() {
    // Arrange
    dlqMessage.setEventId(eventId);
    dlqMessage.setStatus(DlqStatus.FAILED);
    dlqMessage.setPayload(BROKEN_JSON);
    dlqMessage.setErrorClass("JsonProcessingException");
    dlqMessageRepository.save(dlqMessage);

    // Проверяем, что сообщение сохранено в DLQ с статусом FAILED
    assertThat(dlqMessageRepository.findByStatusAndTopic(DlqStatus.FAILED, topic)).hasSize(1);

    // Act
    // Вызываем API ретрая. Даже если API вернет 200 OK (потому что задача "принята"),
    // мы ждем побочного эффекта в БД после работы консьюмера.
    ResponseEntity<String> response =
        testRestTemplate.postForEntity("/dlq/retry/{key}", null, String.class, eventId);

    // Assert HTTP response
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).contains("Retry triggered for messages, key = " + eventId);

    // Проверка, что message был добавлен в таблицу task5_users. Consumer успешно обработал message
    await()
        .atMost(Duration.ofSeconds(10))
        .pollInterval(Duration.ofMillis(500))
        .untilAsserted(
            () -> {
              Optional<DlqMessage> dlqMessageOpt = dlqMessageRepository.findByEventId(eventId);
              assertThat(dlqMessageOpt).isPresent();
              DlqMessage updatedDlqMessage = dlqMessageOpt.get();
              assertThat(updatedDlqMessage.getStatus()).isEqualTo(DlqStatus.EXHAUSTED);
            });
    // Проверяем, что записи не сущеcтвует в таблице task5_users, т.е. сообщение не было успешно
    // обработано
    await()
        .atMost(Duration.ofSeconds(10))
        .pollInterval(Duration.ofMillis(200))
        .untilAsserted(
            () -> {
              Optional<User> userOpt = userRepository.findByEventId(eventId);
              assertThat(userOpt).isNotPresent();
            });
  }

  /*
     Тест проверяет массовый retry через REST API:
      - Подготовка: Сначала мы сохраняем два DlqMessage в статусе FAILED (TimeoutException),
        имитируя ситуацию, когда сообщения только что попали в таблицу task5_dlq_messages.
      - Вызов API: Затем мы вызываем REST API для массового успешного retry (сообщения уходят в главный топи).
      - Ожидание:
           UserConsumer#consumer успешно обработает сообщения из главного топика, сохранит User в БД и вызовет dlqMessageUpdater.deleteIfExists(eventId).
           Сообщения будут удаленs из таблицы task5_dlq_messages и мы будем ждать, пока консьюмер не обработает их.
           Сообщения должны появиться в главном топике и в таблице task5_users.
  */
  @Test
  @DisplayName("Mass retry via REST API should process 2 messages and delete it from DLQ")
  void shouldProcessTwoDlqMessagesOnMassRetry() throws JsonProcessingException {
    // Arrange
    // Сохраняем первое сообщение в DLQ
    dlqMessage.setEventId(eventId);
    dlqMessage.setStatus(DlqStatus.FAILED);
    dlqMessage.setErrorClass("TimeoutException");
    dlqMessageRepository.save(dlqMessage);

    // Сохраняем второе сообщение в DLQ с другим eventId
    String eventId2 = UUID.randomUUID().toString();
    ObjectNode rootNode = (ObjectNode) objectMapper.readTree(jsonTemplate);
    rootNode.put("userId", UUID.randomUUID().toString());
    DlqMessage dlqMessage2 = new DlqMessage();
    dlqMessage2.setPayload(objectMapper.writeValueAsString(rootNode));
    dlqMessage2.setTopic(topic);
    dlqMessage2.setRetryCount(INIT_RETRY_COUNT);
    dlqMessage2.setEventId(eventId2);
    dlqMessage2.setStatus(DlqStatus.FAILED);
    dlqMessage2.setErrorClass("TimeoutException");
    dlqMessageRepository.save(dlqMessage2);

    // Проверяем, что оба сообщения сохранены в DLQ с статусом FAILED
    assertThat(dlqMessageRepository.findByStatusAndTopic(DlqStatus.FAILED, topic)).hasSize(2);

    // Act
    ResponseEntity<String> response =
        testRestTemplate.postForEntity(
            "/dlq/retry?topic=" + topic + "&statuses=" + DlqStatus.FAILED, null, String.class);
    // Assert HTTP response
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isEqualTo("Retry triggered for 2 DLT messages");

    // Проверка, что оба сообщения были добавлены в таблицу task5_users. Consumer успешно их
    // обработал.
    await()
        .atMost(Duration.ofSeconds(20))
        .pollInterval(Duration.ofMillis(500))
        .untilAsserted(
            () -> {
              List<User> userList = userRepository.findAll();
              assertThat(userList).hasSize(2);
            });

    // Проверяем, что запись удалена
    await()
        .atMost(Duration.ofSeconds(10))
        .pollInterval(Duration.ofMillis(200))
        .untilAsserted(
            () -> {
              assertThat(dlqMessageRepository.findByStatusAndTopic(DlqStatus.FAILED, topic))
                  .isEmpty();
            });
  }
}
