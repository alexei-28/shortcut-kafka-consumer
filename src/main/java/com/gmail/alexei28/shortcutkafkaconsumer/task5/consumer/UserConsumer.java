package com.gmail.alexei28.shortcutkafkaconsumer.task5.consumer;

import com.gmail.alexei28.shortcutkafkaconsumer.task5.dto.UserDto;
import com.gmail.alexei28.shortcutkafkaconsumer.task5.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;

/*
   Задача 5 — «DLQ, но удобнее»
    Через полгода команда поняла: DLQ-топик неудобен.
    Нельзя фильтровать, нельзя массово ретраить, операционники тратят по 2 часа в день на kafka-console-consumer.

    Что говорит бизнес:
    Нужна фильтрация по топику, статусу, дате
    Ручной retry одного сообщения и массовый retry
    Возможность пометить «разобрано, не нужно»
    Автоматический retry по расписанию для транзиентных ошибок
    Задание: реализуй DLQ через PostgreSQL вместо Kafka-топика. Таблица, REST API для управления, scheduled job для автоматического retry.
*/
@Service
public class UserConsumer {
  private final UserService userService;
  private static final Logger logger = LoggerFactory.getLogger(UserConsumer.class);

  public UserConsumer(UserService userService) {
    this.userService = userService;
  }

  @KafkaListener(topics = "${app.kafka.topics.task5}", groupId = "${app.kafka.groups.task5}")
  public void consume(@Header(KafkaHeaders.RECEIVED_KEY) String key, UserDto userDto) {
    logger.info("consume, received message with key(eventId): {}, UserDto: {}", key, userDto);
    userService.saveUser(userDto, key);
  }
}
