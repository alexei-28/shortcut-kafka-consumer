package com.gmail.alexei28.shortcut.kafka.consumer.task4;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gmail.alexei28.shortcut.kafka.consumer.task4.dto.UserDto;
import com.gmail.alexei28.shortcut.kafka.consumer.task4.entity.DltMessage;
import com.gmail.alexei28.shortcut.kafka.consumer.task4.entity.User;
import com.gmail.alexei28.shortcut.kafka.consumer.task4.repo.DltMessageRepository;
import com.gmail.alexei28.shortcut.kafka.consumer.task4.repo.UserRepository;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.stream.StreamSupport;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.io.ClassPathResource;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.messaging.converter.MessageConversionException;
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
@SpringBootTest
@Testcontainers
class DltIntegrationTest {
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

  @Value("${app.kafka.topics.task4}")
  private String topic;

  private String topicDlt;

  @Value("${app.kafka.groups.task4}")
  private String consumerGroup;

  // overrideProps вызывается один раз при создании Spring ApplicationContext.
  // Все тесты класса будут работать с одной уникальной группой.
  @DynamicPropertySource
  static void overrideProps(DynamicPropertyRegistry registry) {
    // Берем префикс из проперти
    String prefix = "test-task4-group";
    registry.add("app.kafka.groups.task4", () -> prefix + "-" + UUID.randomUUID());
  }

  @Autowired private KafkaTemplate<String, UserDto> kafkaTemplate;
  @Autowired private KafkaTemplate<String, String> kafkaStringTemplate;
  @Autowired private ObjectMapper objectMapper;
  private static String jsonTemplate;
  private UserDto validUserDto;
  private static final String INALID_INN = "ABC123";
  private static final String INALID_EMAIL = "invalid_email";
  private String eventId;
  private Map<String, Object> consumerProps;
  @Autowired private UserRepository userRepository;
  @Autowired private DltMessageRepository dltMessageRepository;

  @BeforeAll
  static void beforeAll() throws IOException {
    jsonTemplate =
        StreamUtils.copyToString(
            new ClassPathResource("create_user_template.json").getInputStream(),
            StandardCharsets.UTF_8);
  }

  @BeforeEach
  void setUp() throws JsonProcessingException {
    validUserDto = objectMapper.readValue(jsonTemplate, UserDto.class);
    validUserDto.setUserId(UUID.randomUUID());

    topicDlt = topic + "-dlt";

    eventId = UUID.randomUUID().toString();
    consumerProps =
        KafkaTestUtils.consumerProps(
            kafkaContainer.getBootstrapServers(), consumerGroup + "-dlt-" + eventId);
    consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
    consumerProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
    consumerProps.put(
        ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
        org.apache.kafka.common.serialization.StringDeserializer.class);
    consumerProps.put(
        ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
        org.apache.kafka.common.serialization.StringDeserializer.class);
  }

  /*
    Тест, который проверяет, что валидное сообщение обработано,
  */
  @Test
  void shouldProcessValidMessage() throws Exception {
    // Act
    kafkaTemplate.send(topic, eventId, validUserDto).get();

    // Assert
    // Проверяем, что сообщение успешно обработано и не попало в DLT
    try (Consumer<String, String> consumer =
        new DefaultKafkaConsumerFactory<String, String>(consumerProps).createConsumer()) {
      consumer.subscribe(Collections.singletonList(topicDlt));
      ConsumerRecords<String, String> records =
          KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(5));
      assertThat(records.isEmpty()).isTrue();
    }

    // Проверяем, что данные сохранены в БД и соответствуют отправленному DTO
    Optional<User> findUserOpt = userRepository.findByEventId(eventId);
    assertThat(findUserOpt).isPresent();
    User findUser = findUserOpt.get();
    assertThat(findUser.getEventId()).isEqualTo(eventId);
    assertThat(findUser.getFirstName()).isEqualTo(validUserDto.getFirstName());
    assertThat(findUser.getLastName()).isEqualTo(validUserDto.getLastName());
    assertThat(findUser.getEmail()).isEqualTo(validUserDto.getEmail());
    assertThat(findUser.getInn()).isEqualTo(validUserDto.getInn());

    // Проверяем, что в DLT нет сообщений
    try (Consumer<String, String> consumer =
        new DefaultKafkaConsumerFactory<String, String>(consumerProps).createConsumer()) {
      consumer.subscribe(Collections.singletonList(topicDlt));
      ConsumerRecords<String, String> records =
          KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(10));
      assertThat(records).isEmpty();
    }

    // Проверяем, что записи DltMessage нет в БД
    Optional<DltMessage> findDltMessageOpt = dltMessageRepository.findByMessageKey(eventId);
    assertThat(findDltMessageOpt).isEmpty();
  }

  /*
   Тест, который проверяет, что при отправке некорректного JSON сообщения,
   оно попадает в DLT с правильными заголовками ошибки.
  */
  @Test
  void shouldSendMessageToDltWhenJsonBroken() throws Exception {
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
    KafkaTemplate<String, String> kafkaTemplateString =
        new KafkaTemplate<>(kafkaStringTemplate.getProducerFactory());

    // Act
    kafkaTemplateString.send(topic, eventId, brokenJson).get();

    // Assert
    try (Consumer<String, String> consumer =
        new DefaultKafkaConsumerFactory<String, String>(consumerProps).createConsumer()) {
      consumer.subscribe(Collections.singletonList(topicDlt));
      ConsumerRecord<String, String> consumerRecord =
          KafkaTestUtils.getSingleRecord(consumer, topicDlt, Duration.ofSeconds(10));

      assertThat(consumerRecord).isNotNull();
      assertThat(consumerRecord.key()).isEqualTo(eventId);

      String exceptionClass =
          new String(consumerRecord.headers().lastHeader("x-exception-class").value());
      assertThat(exceptionClass).isEqualTo(MessageConversionException.class.getName());
      String exceptionMessage =
          new String(consumerRecord.headers().lastHeader("x-exception-message").value());
      assertThat(exceptionMessage).contains("Cannot convert");
    }
  }

  /*
   Тест, который проверяет, что при отправке сообщения с некорректным INN или email,
   оно попадает в DLT с правильными заголовками ошибки.
  */
  @Test
  void shouldSendMessageToDltWhenInnInvalid() throws Exception {
    // Arrange
    UserDto userDto = objectMapper.readValue(jsonTemplate, UserDto.class);
    userDto.setUserId(UUID.randomUUID());
    userDto.setInn(INALID_INN);

    // Act
    kafkaTemplate.send(topic, eventId, userDto).get();

    // Assert
    try (Consumer<String, String> consumer =
        new DefaultKafkaConsumerFactory<String, String>(consumerProps).createConsumer()) {
      consumer.subscribe(Collections.singletonList(topicDlt));
      ConsumerRecord<String, String> consumerRecord =
          KafkaTestUtils.getSingleRecord(consumer, topicDlt, Duration.ofSeconds(10));

      assertThat(consumerRecord).isNotNull();
      assertThat(consumerRecord.key()).isEqualTo(eventId);

      // Проверяем заголовки ошибки
      String exceptionClass =
          new String(consumerRecord.headers().lastHeader("x-exception-class").value());
      assertThat(exceptionClass).isEqualTo(IllegalArgumentException.class.getName());
      String exceptionMessage =
          new String(consumerRecord.headers().lastHeader("x-exception-message").value());
      assertThat(exceptionMessage).isEqualTo("INN must contain 1-20 digits only");
    }
  }

  /*
       Тест, который проверяет, что при отправке сообщения с некорректным email,
       оно попадает в DLT с правильными заголовками ошибки.
  */
  @Test
  void shouldSendMessageToDltWhenEmailInvalid() throws Exception {
    // Arrange
    UserDto userDto = objectMapper.readValue(jsonTemplate, UserDto.class);
    userDto.setUserId(UUID.randomUUID());
    userDto.setEmail(INALID_EMAIL);

    // Act
    kafkaTemplate.send(topic, eventId, userDto).get();

    // Assert
    try (Consumer<String, String> consumer =
        new DefaultKafkaConsumerFactory<String, String>(consumerProps).createConsumer()) {
      consumer.subscribe(Collections.singletonList(topicDlt));

      ConsumerRecord<String, String> consumerRecord =
          KafkaTestUtils.getSingleRecord(consumer, topicDlt, Duration.ofSeconds(10));

      assertThat(consumerRecord).isNotNull();
      assertThat(consumerRecord.key()).isEqualTo(eventId);

      // Проверяем заголовки ошибки
      String exceptionClass =
          new String(consumerRecord.headers().lastHeader("x-exception-class").value());
      assertThat(exceptionClass).isEqualTo(IllegalArgumentException.class.getName());
      String exceptionMessage =
          new String(consumerRecord.headers().lastHeader("x-exception-message").value());
      assertThat(exceptionMessage).isEqualTo("Invalid email format");
    }
  }

  /*
       Тест проверяет, что после poison pill(ядовитая пилюля) остальные сообщения продолжают обрабатываться.
       Сначала отправляем валидное сообщение, потом сообщение-poison pill, которое не может быть обработано,
       а потом снова валидное сообщение. После обработки проверяем, что после DLT все валидные сообщения успешно дошли
       и обработались, а poison pill ушло в DLT.

       Изоляция тестов через фильтрацию:
       Используя getRecords, вы можете реализовать поиск по конкретному ключу (key или eventId).
       Преимущество: Вашему тесту становится все равно, сколько записей в топике — 1 или 100.
       Он просто просматривает поток, пока не найдет ту,
       которая принадлежит именно текущему тестовому сценарию. Это делает тесты идемпотентными.

       getRecords плюсы:
       - Читает любое количество сообщений, не падает на больше/меньше.
       - Позволяет фильтровать нужное сообщение по key, value или заголовкам.
       - Можно использовать Awaitility, чтобы дождаться конкретного сообщения асинхронно.
  */
  @Test
  void shouldContinueProcessingAfterPoisonPill() throws Exception {
    // Arrange
    // Первое валидное сообщение
    UserDto validUser1 = objectMapper.readValue(jsonTemplate, UserDto.class);
    validUser1.setUserId(UUID.randomUUID());
    String eventId1 = UUID.randomUUID().toString();

    // Сообщение-poison pill (некорректный INN)
    UserDto invalidUserDto = objectMapper.readValue(jsonTemplate, UserDto.class);
    invalidUserDto.setUserId(UUID.randomUUID());
    invalidUserDto.setInn(INALID_INN);
    String poisonEventId = UUID.randomUUID().toString();

    // Второе валидное сообщение
    UserDto validUser2 = objectMapper.readValue(jsonTemplate, UserDto.class);
    validUser2.setUserId(UUID.randomUUID());
    String eventId2 = UUID.randomUUID().toString();

    // Act - отправляем сообщения в порядке: валидное, poison pill, валидное
    kafkaTemplate.send(topic, eventId1, validUser1).get();
    kafkaTemplate.send(topic, poisonEventId, invalidUserDto).get();
    kafkaTemplate.send(topic, eventId2, validUser2).get();

    // Assert
    // Проверяем, что оба валидных сообщения обработаны
    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () -> {
              assertThat(userRepository.findByEventId(eventId1)).isPresent();
              assertThat(userRepository.findByEventId(eventId2)).isPresent();
            });

    // Проверяем, что poison pill попало в DLT
    // Assert
    Map<String, Object> propsForAssert = new HashMap<>(consumerProps);
    propsForAssert.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    try (Consumer<String, String> consumer =
        new DefaultKafkaConsumerFactory<String, String>(propsForAssert).createConsumer()) {
      consumer.subscribe(Collections.singletonList(topicDlt));
      //       Используем await, чтобы дождаться именно НАШЕГО сообщения среди всех записей в топике
      ConsumerRecord<String, String> consumerRecord =
          await()
              .atMost(Duration.ofSeconds(15))
              .pollInterval(Duration.ofMillis(500))
              .until(
                  () -> {
                    // Берем пачку записей
                    ConsumerRecords<String, String> records =
                        KafkaTestUtils.getRecords(consumer, Duration.ofMillis(1000));
                    // Ищем среди них ту, у которой ключ совпадает с нашим poisonEventId
                    return StreamSupport.stream(records.spliterator(), false)
                        .filter(r -> r.key().equals(poisonEventId))
                        .findFirst()
                        .orElse(null);
                  },
                  Objects::nonNull);

      assertThat(consumerRecord).isNotNull();
      assertThat(consumerRecord.key()).isEqualTo(poisonEventId);

      // Проверяем заголовки ошибки
      String exceptionClass =
          new String(consumerRecord.headers().lastHeader("x-exception-class").value());
      assertThat(exceptionClass).isEqualTo(IllegalArgumentException.class.getName());

      String exceptionMessage =
          new String(consumerRecord.headers().lastHeader("x-exception-message").value());
      assertThat(exceptionMessage).isEqualTo("INN must contain 1-20 digits only");
    }

    // Проверяем, что одна poison pill запись попала в БД
    Optional<DltMessage> findPoisonPillOpt = dltMessageRepository.findByMessageKey(poisonEventId);
    assertThat(findPoisonPillOpt).isPresent();

    // Проверяем, что хорошие сообщения не попали в DLT
    assertThat(dltMessageRepository.findByMessageKey(eventId1)).isEmpty();
    assertThat(dltMessageRepository.findByMessageKey(eventId2)).isEmpty();

    // Проверяем наличие валидных сообщений в основном топике
    Map<String, Object> propsForMainTopic = new HashMap<>(consumerProps);
    propsForMainTopic.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    // Используем уникальный Group ID, чтобы не зависеть от прогресса основного приложения
    // создаём «независимого наблюдателя», который заходит в топик с чистого листа,
    // не мешая основному слушателю и не забирая у него партиции.
    // Уникальный UUID в имени группы заставляет Kafka думать, что это совершенно новый участник
    // системы.
    // Для новой группы earliest гарантированно означает чтение с самого первого сообщения,
    // доступного в топике.
    propsForMainTopic.put(ConsumerConfig.GROUP_ID_CONFIG, consumerGroup + "-" + UUID.randomUUID());

    try (Consumer<String, String> consumer =
        new DefaultKafkaConsumerFactory<String, String>(propsForMainTopic).createConsumer()) {
      consumer.subscribe(Collections.singletonList(topic));

      await()
          .atMost(Duration.ofSeconds(10))
          .untilAsserted(
              () -> {
                ConsumerRecords<String, String> records =
                    KafkaTestUtils.getRecords(consumer, Duration.ofMillis(1000));

                List<String> foundKeys =
                    StreamSupport.stream(records.spliterator(), false)
                        .map(ConsumerRecord::key)
                        .toList();

                // Проверяем, что наши eventId присутствуют в топике
                assertThat(foundKeys)
                    .as("Основной топик должен содержать оба валидных сообщения")
                    .contains(eventId1, eventId2);
              });
    }
  }
}
