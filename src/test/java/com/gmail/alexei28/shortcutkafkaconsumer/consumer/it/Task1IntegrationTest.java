package com.gmail.alexei28.shortcutkafkaconsumer.consumer.it;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gmail.alexei28.shortcutkafkaconsumer.consumer.ConsumerTask1;
import com.gmail.alexei28.shortcutkafkaconsumer.dto.Task1Dto;
import com.gmail.alexei28.shortcutkafkaconsumer.repo.Task1Repository;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
@SpringBootTest
@Testcontainers
class Task1IntegrationTest {
  @Value("${app.kafka.topics.task1}")
  private String topic;

  @Value("${app.kafka.groups.task1}")
  private String consumerGroup;

  /*
      Поскольку ConsumerTask1 помечен как @MockitoSpyBean, Spring использует реальный экземпляр,
      но позволяет нам «подсматривать» за его методами через verify.
      @MockitoSpyBean: Позволяет нам следить за реальным бином ConsumerTask1 и считать количествовызовов метода consume.
      Spring создает настоящий экземпляр вашего ConsumerTask1 со всеми его зависимостями (repository, taskMapper).
      Обертка (Spy): Mockito «оборачивает» этот реальный объект.
      Это позволяет вам:
      -Вызывать реальные методы (код внутри consume и process будет выполнен).
      -Следить за вызовами (использовать verify, чтобы посчитать количество вызовов).
      -Переопределять поведение только конкретных методов, если нужно (через doThrow или doReturn).
  */
  @MockitoSpyBean private ConsumerTask1 consumerTask1;

  @Container @ServiceConnection
  static KafkaContainer kafkaContainer =
      new KafkaContainer(DockerImageName.parse("apache/kafka-native:4.1.1"));

  // Важно: PostgreSQLContainer должен быть объявлен после KafkaContainer,
  // так как Spring Boot может пытаться подключиться к БД до того, как Kafka будет готов.
  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgresContainer =
      new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

  @Autowired private Task1Repository repository; // Directly check the DB
  @Autowired private KafkaTemplate<String, Object> kafkaTemplate;
  @Autowired private ObjectMapper objectMapper;
  private Task1Dto producedTask1Dto;
  private static String jsonTemplate;
  private String consumedValidJson;
  private long consumedTask1Number;
  private String consumedTask1Content;

  @BeforeAll
  static void beforeAll() throws IOException {
    jsonTemplate =
        StreamUtils.copyToString(
            new ClassPathResource("task1_template.json").getInputStream(), StandardCharsets.UTF_8);
  }

  @BeforeEach
  void setUp() throws JsonProcessingException {
    // Очищаем базу данных перед каждым тестом
    repository.deleteAll();
    // Сбрасываем моки/спаи, чтобы verify() считал вызовы только текущего теста
    reset(consumerTask1);

    consumedTask1Number = new Random().nextLong(10000);
    consumedTask1Content = "Task1_TEST_CONSUMED_" + consumedTask1Number;
    // Update specific nodes in the JSON
    DocumentContext context =
        JsonPath.parse(jsonTemplate)
            .set("$.number", consumedTask1Number)
            .set("$.content", consumedTask1Content);
    consumedValidJson = context.jsonString();

    // Создаем DTO из строки JSON
    producedTask1Dto = objectMapper.readValue(consumedValidJson, Task1Dto.class);
  }

  @Test
  @DisplayName("Should send to Kafka and consume same object")
  void shouldSendToKafkaAndConsumeSameObject() {
    // Act: Spring сам превратит объект в правильный JSON
    kafkaTemplate.send(topic, producedTask1Dto);

    // Assert: Verify the consumer received the object
    ArgumentCaptor<Task1Dto> captor = ArgumentCaptor.forClass(Task1Dto.class);
    // Ждем именно один вызов, игнорируя промежуточные осечки Awaitility
    await()
        .atMost(5, SECONDS)
        .untilAsserted(
            () -> {
              // Используем atLeast(1), чтобы Awaitility не падал, если он проверит 2 раза во время
              // обработки
              verify(consumerTask1, atLeast(1)).consume(captor.capture(), any());

              Task1Dto consumedTask1Dto = captor.getValue();
              assertThat(consumedTask1Dto).isNotNull().isEqualTo(producedTask1Dto);
            });

    // Проверяем, что в течение следующих 3 секунд новых вызовов ConsumerTask1#consume НЕ поступило
    await()
        .atMost(3, SECONDS)
        .untilAsserted(
            () -> {
              // Проверяем отсутствие новых попыток после паузы
              verifyNoMoreInteractions(consumerTask1);
            });
  }

  @Test
  @DisplayName(
      "Method consume was called once(strategy at-most-once) because processing was success")
  void shouldCallOnlyOnceConsumeWhenProcessingSuccess() {
    // Act: Spring сам превратит объект в правильный JSON
    kafkaTemplate.send(topic, producedTask1Dto);

    // Assert
    // Ждем некоторое время (5 ssc), чтобы consumer успел сработать.
    // Проверяем, что метод consume был вызван ровно 1 раз (at-most-once).
    // Если бы была стратегия retry, вызовов было бы несколько.
    Awaitility.await()
        .atMost(5, SECONDS)
        .untilAsserted(() -> verify(consumerTask1, times(1)).consume(any(), any()));

    // Проверяем, что в репозитории одна запись
    long count = repository.count();
    assertEquals(
        1, count, "Репозиторий должен содержать одну запись, так как обработка была успешна");

    // Проверяем, что в течение следующих 3 секунд новых вызовов ConsumerTask1#consume НЕ поступило
    await()
        .atMost(3, SECONDS)
        .untilAsserted(
            () -> {
              // Проверяем отсутствие новых попыток после паузы
              verifyNoMoreInteractions(consumerTask1);
            });
  }

  @Test
  @DisplayName("Method consume was called once(strategy at-most-once) because processing was fail")
  void shouldCallOnlyOnceConsumeWhenProcessingFails() {
    // Arrange
    // Настраиваем SPY, чтобы он выбросил ошибку ПРИ ОБРАБОТКЕ.
    // Мы имитируем ошибку в базе данных или бизнес-логике.
    doThrow(new RuntimeException("Database connection lost"))
        .when(consumerTask1)
        .consume(any(), any());

    // Act: Spring сам превратит объект в правильный JSON
    kafkaTemplate.send(topic, producedTask1Dto);

    // Assert
    // Ждем некоторое время (5 ssc), чтобы consumer успел сработать.
    // Проверяем, что метод consume был вызван ровно 1 раз (at-most-once).
    // Если бы была стратегия retry, вызовов было бы несколько.
    Awaitility.await()
        .atMost(5, SECONDS)
        .untilAsserted(() -> verify(consumerTask1, times(1)).consume(any(), any()));

    // Проверяем, что в репозитории пусто (данные не сохранились из-за ошибки)
    long count = repository.count();
    assertEquals(0, count, "Репозиторий должен быть пуст, так как обработка упала");

    // Проверяем, что в течение следующих 3 секунд новых вызовов ConsumerTask1#consume НЕ поступило
    await()
        .atMost(3, SECONDS)
        .untilAsserted(
            () -> {
              // Проверяем отсутствие новых попыток после паузы
              verifyNoMoreInteractions(consumerTask1);
            });
  }
}
