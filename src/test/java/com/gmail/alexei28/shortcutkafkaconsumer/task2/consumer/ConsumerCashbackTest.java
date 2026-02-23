package com.gmail.alexei28.shortcutkafkaconsumer.task2.consumer;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gmail.alexei28.shortcutkafkaconsumer.task2.dto.CashbackDto;
import com.gmail.alexei28.shortcutkafkaconsumer.task2.entity.Cashback;
import com.gmail.alexei28.shortcutkafkaconsumer.task2.entity.CashbackStatus;
import com.gmail.alexei28.shortcutkafkaconsumer.task2.repo.CashbackRepository;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.util.StreamUtils;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

/*
   В Kafka единственный observable-признак, что ack.acknowledge() был вызван - сообщение больше не читается повторно
   этим же consumer group.
   Acknowledgment создаёт сам Spring-Kafka контейнер и его в тесте напрямую не получить.
   Поэтому в интеграционном тесте проверяется не сам объект ack, а факт, что listener завершился успешно и offset был закоммичен.
   А в Kafka единственный observable-признак,
   что ack.acknowledge() -> был вызван сообщение больше не читается повторно этим же consumer group.

*/
@ActiveProfiles("test")
@SpringBootTest(
    properties = {
      "spring.kafka.consumer.auto-offset-reset=earliest",
      "spring.kafka.consumer.enable-auto-commit=false",
      "spring.kafka.listener.ack-mode=manual"
    })
@Testcontainers
class ConsumerCashbackTest {
  @Value("${app.kafka.topics.task2}")
  private String topic;

  @Value("${app.kafka.groups.task2}")
  private String consumerGroup;

  @Container @ServiceConnection
  static KafkaContainer kafkaContainer =
      new KafkaContainer(DockerImageName.parse("apache/kafka-native:4.1.1"));

  // Важно: PostgreSQLContainer должен быть объявлен после KafkaContainer,
  // так как Spring Boot может пытаться подключиться к БД до того, как Kafka будет готов.
  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgresContainer =
      new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

  /*
      Поскольку CashbackRepository помечен как @MockitoSpyBean, Spring использует реальный экземпляр,
      но позволяет нам «подсматривать» за его методами через verify.
      @MockitoSpyBean: Позволяет нам следить за реальным бином ConsumerTask1 и считать количествовызовов метода consume.
      Spring создает настоящий экземпляр вашего ConsumerTask1 со всеми его зависимостями (repository, taskMapper).
      Обертка (Spy): Mockito «оборачивает» этот реальный объект.
      Это позволяет вам:
      -Вызывать реальные методы (код внутри consume и process будет выполнен).
      -Следить за вызовами (использовать verify, чтобы посчитать количество вызовов).
      -Переопределять поведение только конкретных методов, если нужно (через doThrow или doReturn).
  */
  @MockitoSpyBean private CashbackRepository cashbackRepositorySpy;
  @MockitoSpyBean private ConsumerCashback consumerCashbackSpy;
  @Autowired private KafkaTemplate<String, Object> kafkaTemplate;
  @Autowired private ObjectMapper objectMapper;
  private CashbackDto producedCashbackDto;
  private static String jsonTemplate;

  @BeforeAll
  static void beforeAll() throws IOException {
    jsonTemplate =
        StreamUtils.copyToString(
            new ClassPathResource("cashback_template.json").getInputStream(),
            StandardCharsets.UTF_8);
  }

  @BeforeEach
  void setUp() throws JsonProcessingException {
    // Очищаем базу данных перед каждым тестом
    cashbackRepositorySpy.deleteAll();
    // Сбрасываем моки/спаи, чтобы verify() считал вызовы только текущего теста
    reset(consumerCashbackSpy);

    CashbackStatus[] statuses = CashbackStatus.values();
    // Update specific nodes in the JSON
    DocumentContext context =
        JsonPath.parse(jsonTemplate)
            .set("$.eventId", UUID.randomUUID().toString())
            .set("$.userId", new Random().nextLong(10000))
            .set("$.orderId", "ORD-" + new Random().nextLong(10000))
            .set("$.status", statuses[new Random().nextInt(statuses.length)]) // Случайный статус
            .set("$.createdAt", LocalDateTime.now().minusHours(12).toString())
            .set("$.appliedAt", LocalDateTime.now().minusHours(10).toString())
            .set("$.receivedAt", LocalDateTime.now().toString());
    String consumedValidJson = context.jsonString();

    // Создаем DTO из строки JSON
    producedCashbackDto = objectMapper.readValue(consumedValidJson, CashbackDto.class);
  }

  /*
      Позитивный сценарий:
        1. ConsumerCashback.consume() вызывается
        2. запись сохраняется в Postgres
        3. вызывается ack.acknowledge()
        4. offset фиксируется
        5. при новом poll consumer group не получает это сообщение повторно

        Если ack НЕ вызвался -> Kafka пришлёт сообщение снова.

        Почему это реально проверяет acknowledge()
        Kafka работает так:
        -ack вызван -> offset commit -> сообщение больше не приходит
        -ack НЕ вызван -> consumer re-pull -> сообщение придёт снова
  */
  @Test
  @DisplayName("Kafka should send only once CashbackDto when repo save successfully")
  void shouldCallConsumeOnlyOnceWhenRepoSaveSuccess() {
    // Act
    // Отправляем сообщение
    kafkaTemplate.send(topic, producedCashbackDto.getEventId(), producedCashbackDto);

    // Assert
    // Ждём пока отработает метод consume

    await()
        .atMost(15, SECONDS)
        .untilAsserted( // повторяет assert/verify, пока не пройдет
            () ->
                verify(consumerCashbackSpy, times(1))
                    .consume(any(CashbackDto.class), any(Acknowledgment.class)));

    // Если ack.acknowledge() был вызван -> Kafka снова Не пришлет сообщение, поэтому проверяем что
    // запись в БД только 1
    await()
        .during(5, SECONDS) // наблюдаем 5 секунд
        .atMost(7, SECONDS)
        .untilAsserted(
            () -> {
              assertEquals(
                  1,
                  cashbackRepositorySpy.count(),
                  "Cashback was re-consumed -> ack.acknowledge() was NOT called");
            });
  }

  /*
    Негативный сценарий:
    Мы должны искусственно уронить сохранение в БД(e,g, DataAccessResourceFailureException), но при этом:
      - Kafka listener продолжит жить
      - offset не зафиксируется (метод ack.acknowledge() не будет вызван)
      - Kafka перешлёт сообщение снова

    Т.е. метод consume() будет вызван 2+ раза.

    Что сейчас реально проверяет этот тест:
      poll -> listener -> exception -> no ack -> offset not committed -> rebalance/poll -> снова listener
  */
  @Test
  @DisplayName("Kafka should retry send CashbackDto when repo save failed")
  void shouldRetryConsumeOnlyOnceWhenRepoSaveFail() {
    // Arrange
    // Заставляем БД падать
    doThrow(new DataAccessResourceFailureException("DB is down"))
        .when(cashbackRepositorySpy)
        .save(any(Cashback.class));
    // Act
    // Отправляем сообщение
    kafkaTemplate.send(topic, producedCashbackDto.getEventId(), producedCashbackDto);

    // Assert
    // Ждём первый вызов consume
    await()
        .atMost(15, SECONDS)
        .untilAsserted( // повторяет assert/verify, пока не пройдет
            () ->
                verify(consumerCashbackSpy, times(1))
                    .consume(any(CashbackDto.class), any(Acknowledgment.class)));

    // Если ack НЕ был вызван -> Kafka пришлёт сообщение снова
    await()
        .during(10, SECONDS)
        .atMost(20, SECONDS)
        .untilAsserted( // повторяет assert/verify, пока не пройдет
            () -> {
              // consume должен вызваться более одного раза
              verify(consumerCashbackSpy, atLeast(2))
                  .consume(any(CashbackDto.class), any(Acknowledgment.class));
            });

    // Убеждаемся, что в БД ничего не сохранилось
    assertThat(cashbackRepositorySpy.count()).isZero();
  }

  /*
    Негативный сценарий:
    - duplicate eventId (DataIntegrityViolationException) -> данные уже есть -> ACK ДОЛЖЕН БЫТЬ.

    Как реально проверить duplicate:
    Нужно вызвать реальный unique constraint БД.
      1. Мы отправляем одно и то же сообщение 2 раза.
      2. PostgreSQL -> unique index -> DataIntegrityViolationException.

    Что этот тест доказал.
    Сервис не создаст 2 кэшбэка клиенту при повторной доставке Kafka.
    Последовательность:
      1-е сообщение -> save -> ACK -> offset commit
      2-е сообщение -> unique violation -> catch -> ACK -> offset commit
      Kafka -> stop retry
  */
  @Test
  void should_acknowledge_duplicate_event_and_not_retry() {
    // -------- FIRST DELIVERY (нормальная обработка) ----------
    kafkaTemplate.send(topic, producedCashbackDto.getEventId(), producedCashbackDto);

    // ждём пока сохранится
    await()
        .atMost(15, SECONDS)
        .untilAsserted(
            () -> { // повторяет assert/verify, пока не пройдет
              Optional<Cashback> savedCashBack =
                  cashbackRepositorySpy.findByEventId(producedCashbackDto.getEventId());
              assertThat(savedCashBack).isPresent();
            });

    // убедились что consume вызвался один раз
    await()
        .atMost(10, SECONDS)
        .untilAsserted(
            () -> // повторяет assert/verify, пока не пройдет
            verify(consumerCashbackSpy, times(1))
                    .consume(any(CashbackDto.class), any(Acknowledgment.class)));

    // Проверяем, что запись в БД только 1
    long firstCount = cashbackRepositorySpy.count();
    assertThat(firstCount).isEqualTo(1);

    // -------- SECOND DELIVERY (ОТСЫЛАЕМ ДУБЛИКАТ, тот же eventId) ----------
    kafkaTemplate.send(topic, producedCashbackDto.getEventId(), producedCashbackDto);

    // Даём Kafka время на retry
    await().pollDelay(4, SECONDS).until(() -> true);

    // В БД всё ещё 1 запись
    assertThat(cashbackRepositorySpy.count())
        .as("Duplicate message should not be saved twice")
        .isEqualTo(1);

    // Kafka делает retry(повторно отсылает CashbackDto - 10 раз за 5 сек).
    // Метод consume был вызван еще раз (дубликат реально пришёл)
    await()
        .atMost(10, SECONDS)
        .untilAsserted(
            () -> // повторяет assert/verify, пока не пройдет
            verify(consumerCashbackSpy, atLeast(2))
                    .consume(any(CashbackDto.class), any(Acknowledgment.class)));

    // НЕТ повторных доставок (ACK был!).
    // Если ack не вызвать -> Kafka будет бесконечно ретраить.
    // Убеждаемся, что в БД сохранилась только одна запись.
    await()
        .during(5, SECONDS)
        .atMost(8, SECONDS)
        .untilAsserted(
            () -> // повторяет assert/verify, пока не пройдет
            assertThat(cashbackRepositorySpy.count()).isEqualTo(1));
  }
}
