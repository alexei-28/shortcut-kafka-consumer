package com.gmail.alexei28.shortcutkafkaconsumer.task4;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gmail.alexei28.shortcutkafkaconsumer.task4.dto.UserDto;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
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
import org.springframework.util.StreamUtils;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

@ActiveProfiles("test")
@SpringBootTest
class DltIntegrationTest {
  @Value("${app.kafka.topics.task4}")
  private String topic;

  private String topicDlt;

  @Value("${app.kafka.groups.task4}")
  private String consumerGroup;

  @Container @ServiceConnection
  static KafkaContainer kafkaContainer =
      new KafkaContainer(DockerImageName.parse("apache/kafka-native:4.1.1"));

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgresContainer =
      new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

  @Autowired private KafkaTemplate<String, UserDto> kafkaTemplate;
  @Autowired private KafkaTemplate<String, String> kafkaStringTemplate;
  @Autowired private ObjectMapper objectMapper;
  private static String jsonTemplate;
  private UserDto validUserDto;
  private static final String INALID_INN = "ABC123";
  private static final String INALID_EMAIL = "invalid_email";
  private String eventId;
  private Map<String, Object> consumerProps;

  @BeforeAll
  static void beforeAll() throws IOException {
    jsonTemplate =
        StreamUtils.copyToString(
            new ClassPathResource("create_user_template.json").getInputStream(),
            StandardCharsets.UTF_8);
  }

  @BeforeEach
  void setUp() throws JsonProcessingException {
    topicDlt = topic + "-dlt";
    validUserDto = objectMapper.readValue(jsonTemplate, UserDto.class);
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
}
