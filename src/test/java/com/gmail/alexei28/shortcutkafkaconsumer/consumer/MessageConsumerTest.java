package com.gmail.alexei28.shortcutkafkaconsumer.consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.gmail.alexei28.shortcutkafkaconsumer.dto.Message;
import com.gmail.alexei28.shortcutkafkaconsumer.repo.MessageRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MessageConsumerTest {
  private static final String TOPIC = "test_message_topic";
  private static final String CONSUMER_GROUP = "test-message-consumer-group";
  @Mock private MessageRepository messageRepositoryMock;
  private MessageConsumer messageConsumer;
  private Message consumedMessage;

  @BeforeEach
  void setUp() {
    messageConsumer = new MessageConsumer(messageRepositoryMock);
    consumedMessage = new Message(123, "Message_TEST_" + 123, LocalDateTime.now());
  }

  @Test
  @DisplayName("Consumer should save message to repository")
  void consumerShouldSaveMessageToRepository() {
    // Arrange
    int partition = 0;
    long offset = 100L;

    // Act
    messageConsumer.consume(TOPIC, CONSUMER_GROUP, partition, offset, consumedMessage);

    // Assert
    verify(messageRepositoryMock).save(consumedMessage);
  }

  @Test
  @DisplayName("Consume should throw exception when repo is fail")
  void consumeShouldThrowExceptionWhenRepositoryFails() {
    // Arrange
    doThrow(new RuntimeException("Database connection error"))
        .when(messageRepositoryMock)
        .save(any(Message.class));

    // Act and Assert
    assertThrows(
        RuntimeException.class,
        () -> {
          messageConsumer.consume(TOPIC, CONSUMER_GROUP, 0, 1L, consumedMessage);
        });
    verify(messageRepositoryMock).save(consumedMessage);
  }
}
