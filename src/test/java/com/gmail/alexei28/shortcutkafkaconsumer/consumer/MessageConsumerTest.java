package com.gmail.alexei28.shortcutkafkaconsumer.consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.gmail.alexei28.shortcutkafkaconsumer.entity.Message;
import com.gmail.alexei28.shortcutkafkaconsumer.repo.MessageRepository;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

@ExtendWith(MockitoExtension.class)
class MessageConsumerTest {
  @Mock private MessageRepository repository;
  @Spy // Используем Spy, чтобы работал реальный маппинг, как в Spring
  private ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
  @InjectMocks private MessageConsumer messageConsumer;

  private static final String TOPIC = "test_message_topic";
  private static final String CONSUMER_GROUP = "test-message-consumer-group";
  private long randomNumber;
  private static String jsonTemplate;
  private String consumedValidJson;
  private static final String CONSUMED_INVALID_JSON = "{ invalid }";

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
    // Update specific nodes in the JSON
    DocumentContext context =
        JsonPath.parse(jsonTemplate)
            .set("$.number", randomNumber)
            .set("$.content", "Message_TEST_CONSUMED" + randomNumber);
    consumedValidJson = context.jsonString();
  }

  @Test
  @DisplayName("Should consume valid json")
  void shouldConsumeSuccess() throws Exception {
    // Act
    messageConsumer.consume(TOPIC, CONSUMER_GROUP, anyInt(), anyLong(), consumedValidJson);

    // Assert
    // Проверяем, что ObjectMapper действительно вызывался для десериализации
    verify(objectMapper).readValue(eq(consumedValidJson), eq(Message.class));
    // Проверяем, что repository.save() был вызван
    verify(repository).save(any(Message.class));
  }

  @Test
  @DisplayName("Should not save to repo invalid json")
  void shouldNotSaveToRepoWhenInvalidJson() {
    // Act
    messageConsumer.consume(TOPIC, CONSUMER_GROUP, anyInt(), anyLong(), CONSUMED_INVALID_JSON);

    // Assert
    // Репозиторий не должен вызываться, так как упало исключение
    verify(repository, never()).save(any());
  }
}
