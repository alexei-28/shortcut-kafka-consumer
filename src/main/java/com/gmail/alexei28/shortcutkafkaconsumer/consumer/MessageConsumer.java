package com.gmail.alexei28.shortcutkafkaconsumer.consumer;

import com.gmail.alexei28.shortcutkafkaconsumer.dto.Message;
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
*/
@Service
public class MessageConsumer {
  private final MessageRepository repository;
  private static final Logger logger = LoggerFactory.getLogger(MessageConsumer.class);

  public MessageConsumer(MessageRepository repository) {
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
      Message message) {
    logger.info(
        "consume, topic: '{}', groupId: {}, partition: {}, offset: {}, messageLog: {}",
        topic,
        groupId,
        partition,
        offset,
        message);
    repository.save(message);
    logger.info("consume, messageLog successfully saved to database: {}", message);
  }
}
