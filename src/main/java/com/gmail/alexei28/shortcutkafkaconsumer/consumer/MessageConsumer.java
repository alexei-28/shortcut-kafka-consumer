package com.gmail.alexei28.shortcutkafkaconsumer.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gmail.alexei28.shortcutkafkaconsumer.entity.Message;
import com.gmail.alexei28.shortcutkafkaconsumer.repo.MessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/*
   Kafka Listener: При появлении нового сообщения в топике ${app.kafka.topics.message}, Spring Kafka перехватывает его.
   Десериализация: Строка (в данном примере) попадает в метод consume.
   Persistance: С помощью Spring Data JPA создается объект MessageLog, который сохраняется в таблицу PostgreSQL.

   Контейнер Spring Kafka настроен, по умолчанию BATCH, на автоматическое подтверждение сообщений(commit offset).
   https://docs.spring.io/spring-kafka/reference/kafka/receiving-messages/message-listener-container.html#committing-offsets
   By default Committing Offsets = BATCH - Commit the offset when all the records returned by the poll() have been processed.
   В Spring Kafka,by default,настройка AckMode.BATCH означает,что контейнер будет фиксировать смещения(offsets) только после того,
   как все записи, полученные в результате одного вызова poll(), будут обработаны без исключений.
   Т.е это стратегия "at-least-once" (как минимум один раз):
    poll -> process messages -> commit offset
   То есть сначала обработка -> потом commit. Если consumer умер между обработкой и commit -> Kafka пришлёт ещё раз.

*/
@Service
public class MessageConsumer {
  private final MessageRepository repository;
  private final ObjectMapper objectMapper;
  private static final Logger logger = LoggerFactory.getLogger(MessageConsumer.class);

  public MessageConsumer(ObjectMapper objectMapper, MessageRepository repository) {
    this.objectMapper = objectMapper;
    this.repository = repository;
  }

  /*
      @Transactional - Как это работает в случае сбоя:
      Попытка 1: repository.save() падает (например, база временно недоступна).
      Откат: Транзакция в БД откатывается.
      Retry: AppConfiguration#errorHandler ждет 2 секунды и снова вызывает метод consume с тем же сообщением.
      Финал: Если после 3 попыток база не ожила, сообщение будет либо пропущено (офсет закоммитится),
             либо отправлено в DLT (если вы его настроите).
  */
  @Transactional
  @KafkaListener(topics = "${app.kafka.topics.message}", groupId = "${app.kafka.groups.message}")
  public void consume(
      @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
      @Header(KafkaHeaders.GROUP_ID) String groupId,
      @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
      @Header(KafkaHeaders.OFFSET) long offset,
      String value) {
    try {
      logger.info(
          "consume, topic: '{}', groupId: {}, partition: {}, offset: {}, value: {}",
          topic,
          groupId,
          partition,
          offset,
          value);

      Message message = objectMapper.readValue(value, Message.class);
      repository.save(message);
      logger.info("consume, message successfully saved to database: {}", message);
    } catch (JsonProcessingException e) {
      logger.error("consume, Failed to parse JSON", e);
    }
  }
}
