package com.gmail.alexei28.shortcutkafkaconsumer.task5;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gmail.alexei28.shortcutkafkaconsumer.task5.dto.UserDto;
import com.gmail.alexei28.shortcutkafkaconsumer.task5.entity.DlqMessage;
import com.gmail.alexei28.shortcutkafkaconsumer.task5.entity.DlqStatus;
import com.gmail.alexei28.shortcutkafkaconsumer.task5.repo.DlqMessageRepository;
import com.gmail.alexei28.shortcutkafkaconsumer.task5.service.DlqRetrySchedulerService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.StreamUtils;

@ActiveProfiles("test")
@SpringBootTest
class DlqIntegrationTest {

  @Value("${app.kafka.topics.task5}")
  private String topic;

  @Value("${app.kafka.retry.max-attempts}")
  private int maxAttempts;

  @Autowired private KafkaTemplate<String, UserDto> kafkaTemplate;

  @Autowired private DlqMessageRepository dlqMessageRepository;
  @Autowired private DlqRetrySchedulerService dlqRetrySchedulerService;

  @Autowired private ObjectMapper objectMapper;
  private static String jsonTemplate;
  private static final String INVALID_INN = "ABC123";
  private static final String INVALID_EMAIL = "invalid-email";
  private String eventId;
  @Autowired private KafkaTemplate<String, String> kafkaStringTemplate;

  private UserDto userDto;
  private DlqMessage dlqMessage;

  @BeforeAll
  static void beforeAll() throws IOException {
    jsonTemplate =
        StreamUtils.copyToString(
            new ClassPathResource("create_user_template.json").getInputStream(),
            StandardCharsets.UTF_8);
  }

  @BeforeEach
  void setUp() throws JsonProcessingException {
    eventId = UUID.randomUUID().toString();
    userDto = objectMapper.readValue(jsonTemplate, UserDto.class);
    dlqMessage = new DlqMessage();
    dlqMessage.setEventId(eventId);
    dlqMessage.setPayload(objectMapper.writeValueAsString(userDto));
    dlqMessage.setTopic(topic);
    dlqMessage.setStatus(DlqStatus.NEW);
    dlqMessage.setRetryCount(0);
  }

  /*
   Что тест проверяет:
     - Маршрутизация ошибки: Что DefaultErrorHandler успешно перехватил DeserializationException.
     - Сохранность данных: Что «битый» payload не потерялся и записан в БД именно в том виде, в котором пришел.
     - Метаданные: Что код в KafkaConsumerConfig#errorHandler корректно извлек topic, partition и offset из consumerRecord,
       даже если само тело сообщения не удалось распарсить.
  */
  @Test
  @DisplayName("Should save message to DLT table when invalid JSON is received")
  void shouldSaveToDltWhenJsonIsInvalid() {
    // Arrange
    String brokenJson =
        """
                                  {
                                    "userId": "123",
                                    "firstName": "Alexei",
                                    "lastName": "Ivanov",
                                    "email": "alexei@test.com",
                                    "inn": 12345   // <-- нет кавычек + оборван JSON
                              """;

    // Act
    // Используем StringTemplate, чтобы отправить сырую строку в обход десериализатора на стороне
    // продюсера
    kafkaStringTemplate.send(topic, eventId, brokenJson);

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
   Что тест проверяет:
   - Прохождение десериализации: В отличие от первого теста с brokenJson, здесь используется kafkaTemplate.send(..., UserDto).
     Jackson создаст валидный JSON, и консьюмер успешно превратит его в объект.
   - Бизнес-ошибка: Метод saveUser вызовет validate(userDto), который увидит "ABC123" и выбросит IllegalArgumentException.
   - Перехват: Spring Kafka перехватит это исключение.
     Если CommonErrorHandler настроен на запись в БД при любых ошибках (включая бизнес-логику),
     запись в dlqMessageRepository появится автоматически.
  */
  @Test
  @DisplayName("Should save to DLQ when INN contains non-digits (IllegalArgumentException)")
  void shouldSaveToDlqWhenInnHasInvalidFormat() {
    // Arrange
    // Подготавливаем DTO с буквами в ИНН (нарушает паттерн ^\\d{1,20}$)
    userDto.setInn(INVALID_INN);

    // Act
    // Отправляем корректный JSON (через KafkaTemplate<String, UserDto>),
    // чтобы упасть именно на бизнес-валидации, а не на десериализации
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

  @Test
  @DisplayName("Should save to DLQ when Email format is invalid")
  void shouldSaveToDlqWhenEmailIsInvalid() {
    String localEventId = UUID.randomUUID().toString();
    userDto.setEmail(INVALID_EMAIL);

    kafkaTemplate.send(topic, localEventId, userDto);

    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () -> {
              Optional<DlqMessage> dlqMessageOpt = dlqMessageRepository.findByEventId(localEventId);
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
    dlqMessage.setStatus(DlqStatus.FAILED);
    // Create a message already at the limit
    dlqMessage.setRetryCount(maxAttempts);
    dlqMessageRepository.save(dlqMessage);

    // Act
    dlqRetrySchedulerService.retryMessages();

    // Assert
    await()
        .atMost(Duration.ofSeconds(15)) // Give Kafka time to consume and ErrorHandler to save
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
}
