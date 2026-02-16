package com.gmail.alexei28.shortcutkafkaconsumer.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gmail.alexei28.shortcutkafkaconsumer.entity.Message;
import com.gmail.alexei28.shortcutkafkaconsumer.repo.MessageRepository;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.Random;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
class MessageIntegrationTest {
  @Value("${app.kafka.topics.message}")
  private String topic;

  @Value("${app.kafka.groups.message}")
  private String consumerGroup;

  @Container @ServiceConnection
  static KafkaContainer kafkaContainer =
      new KafkaContainer(DockerImageName.parse("apache/kafka-native:4.1.1"));

  // Важно: PostgreSQLContainer должен быть объявлен после KafkaContainer,
  // так как Spring Boot может пытаться подключиться к БД до того, как Kafka будет готов.
  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgresContainer =
      new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

  @Autowired private MessageRepository repository; // Directly check the DB
  private String consumedMessageContent;
  @Autowired private KafkaTemplate<String, String> kafkaTemplate;
  @Autowired private ObjectMapper objectMapper;
  // Поскольку MessageConsumer помечен как @MockitoSpyBean, Spring использует реальный экземпляр,
  // но позволяет нам «подсматривать» за его методами через verify.
  @MockitoSpyBean private MessageConsumer messageConsumerMock;
  private long randomNumber;
  private static String jsonTemplate;
  private String consumedValidJson;
  private static final Logger logger = LoggerFactory.getLogger(MessageIntegrationTest.class);

  @BeforeAll
  static void beforeAll() throws IOException {
    jsonTemplate =
        StreamUtils.copyToString(
            new ClassPathResource("message_template.json").getInputStream(),
            StandardCharsets.UTF_8);
  }

  @BeforeEach
  void setUp() {
    randomNumber = new Random().nextLong(10000);
    consumedMessageContent = "Message_TEST_CONSUMED_" + randomNumber;
    // Update specific nodes in the JSON
    DocumentContext context =
        JsonPath.parse(jsonTemplate)
            .set("$.number", randomNumber)
            .set("$.content", consumedMessageContent);
    consumedValidJson = context.jsonString();
  }

  @Test
  @DisplayName("Should consume and save message to repo")
  void shouldConsumeAndSaveMessageToRepo() {
    // Act
    kafkaTemplate.send(topic, consumedValidJson);

    // Assert
    // Use Awaitility because Kafka consumption is asynchronous
    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () -> {
              Optional<Message> actualMessageContent = repository.findByNumber(randomNumber);
              assertThat(actualMessageContent).isPresent();
              assertThat(actualMessageContent.get().getContent()).isEqualTo(consumedMessageContent);
            });
  }
}
