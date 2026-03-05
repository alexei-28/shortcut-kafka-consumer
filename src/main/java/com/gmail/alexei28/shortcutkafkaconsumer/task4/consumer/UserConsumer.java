package com.gmail.alexei28.shortcutkafkaconsumer.task4.consumer;

import com.gmail.alexei28.shortcutkafkaconsumer.task4.dto.UserDto;
import com.gmail.alexei28.shortcutkafkaconsumer.task4.service.UserService;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;

/*
    Задача 4 — «Битые сообщения из CFT»:
    customers-sync-processor получает обновления клиентов из CFT. Иногда приходят невалидные события — плохой ИНН, нет адреса.
    Вчера одно такое сообщение заблокировало consumer на 40 минут, лаг рос, данные не обновлялись.
    Что говорит бизнес:
    Плохое сообщение не должно блокировать остальные.
    Нужно видеть все проблемные сообщения в одном месте и уметь переотправить после фикса.
    Решение через отдельный Kafka-топик для «мёртвых» сообщений.
    Задание: реализуй механизм изоляции ошибочных сообщений через DLQ-топик.
    Настрой retry-политику, обогати сообщение метаинформацией об ошибке, сделай эндпоинт для ручной переотправки.

    Решение:
    В архитектуре Kafka-потребителей (Consumers) обычно выделяют два сценария:
    - Deserialization Errors (Ошибки десериализации): Данные в топике — «мусор», который нельзя превратить в объект UserDto.
    - Validation/Business Errors (Ошибки валидации): Формат верный, но данные логически неверны (например, пустой email).

    DeadLetterPublishingRecoverer отправляет сообщение в DLQ через producer.
    Если JSON некорректный (e.g. lastName без закрывающей кавычки), десериализация на уровне ErrorHandlingDeserializer
    произойдет раньше, чем вызов consume. То есть consume не вызовется, и DLQ должен сработать через DefaultErrorHandler.

    - Retry policy (для БД) - KafkaConsumerConfig
    - Обогащение DLT сообщения метаданными об ошибке - KafkaConsumerConfig
✅   - REST endpoint для ручного переотправления из DLT обратно в основной topic - DltController

    Сообщение проходит несколько стадий:
    Kafka bytes
       ↓
    Deserializer (Kafka)
       ↓
    Spring MessageConverter
       ↓
    @KafkaListener method
*/
@Service
public class UserConsumer {
  private final UserService userService;
  private static final Logger logger = LoggerFactory.getLogger(UserConsumer.class);

  public UserConsumer(UserService userService) {
    this.userService = userService;
  }

  @KafkaListener(topics = "${app.kafka.topics.task4}", groupId = "${app.kafka.groups.task4}")
  public void consume(@Header(KafkaHeaders.RECEIVED_KEY) String key, UserDto userDto) {
    logger.info("consume, received message with key(eventId): {}, UserDto: {}", key, userDto);
    userDto.setEventId(UUID.fromString(key));
    userService.saveUser(userDto);
  }
}
