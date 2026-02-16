package com.gmail.alexei28.shortcutkafkaconsumer.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.*;

import com.gmail.alexei28.shortcutkafkaconsumer.dto.Message;
import com.gmail.alexei28.shortcutkafkaconsumer.repo.MessageRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
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
  private Message consumedMessage;
  private String consumedMessageContent;
  @Autowired private KafkaTemplate<String, Message> kafkaTemplate;
  // Поскольку MessageConsumer помечен как @MockitoSpyBean, Spring использует реальный экземпляр,
  // но позволяет нам «подсматривать» за его методами через verify.
  @MockitoSpyBean private MessageConsumer messageConsumerMock;

  private static final Logger logger = LoggerFactory.getLogger(MessageIntegrationTest.class);

  @BeforeEach
  void setUp() {
    consumedMessageContent = "Message_Test_".concat(String.valueOf(System.currentTimeMillis()));
    consumedMessage =
        new Message(System.currentTimeMillis(), consumedMessageContent, LocalDateTime.now());
  }

  @Test
  @DisplayName("Should consume and save message to repo")
  void shouldConsumeAndSaveMessageToRepo() {
    // Act
    kafkaTemplate.send(topic, consumedMessage);

    // Assert
    // Use Awaitility because Kafka consumption is asynchronous
    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () -> {
              Optional<Message> actualMessage = repository.findById(1L);
              assertThat(actualMessage).isPresent();
              assertThat(actualMessage.get().getContent()).isEqualTo(consumedMessageContent);
            });
  }
}
