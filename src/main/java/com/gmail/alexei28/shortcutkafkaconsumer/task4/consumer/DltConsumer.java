package com.gmail.alexei28.shortcutkafkaconsumer.task4.consumer;

import com.gmail.alexei28.shortcutkafkaconsumer.task4.service.DltService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

/*
    DltConsumer не делает запросы по таймеру, а непрерывно poll-ит топик.
    С сообщением в task4-topic-dlt (в самой Кафке) делать ничего не нужно.
    В Кафке нельзя «удалить» или «отредактировать» конкретное сообщение по ключу, как в базе данных.
    Оно просто остается там до тех пор, пока не истечет время хранения топика (retention policy).

    Жизненный цикл сообщения в архитектуре:
    1. Ошибка: Сообщение упало в task4-topic-dlt.
    2. Состояние: Ваш DltConsumer вычитал его и создал «проекцию» (копию) этого сообщения в PostgreSQL.
    3. Исправление: Вы исправили JSON в базе данных и через DltController отправили его в основной топик.
    4. Результат: Основной консьюмер успешно обработал исправленный JSON и удалил запись из БД.

    Что в итоге в топике DLT?
    В топике task4-topic-dlt по-прежнему лежит «битое» (исходное) сообщение.
    - DltConsumer его уже обработал (сдвинул offset). Он не будет читать его снова, если вы не сделаете seek (сброс) офсетов.
    - Сообщение удалится само через время (по умолчанию в Kafka это 7 дней, параметр retention.ms).

    Flow:
    DLT topic
      ↓
    DLT consumer
      ↓
    Postgres

   - Kafka хранит raw message
️   - БД хранит операционную копию
*/

@Service
public class DltConsumer {
  private final DltService dltService;
  private static final Logger logger = LoggerFactory.getLogger(DltConsumer.class);

  public DltConsumer(DltService dltService) {
    this.dltService = dltService;
  }

  @KafkaListener(
      topics = "${app.kafka.topics.task4}-dlt",
      groupId = "${app.kafka.groups.task4}-dlt",
      containerFactory = "dltStringContainerFactory")
  public void consume(ConsumerRecord<String, Object> consumerRecord) {
    logger.info("consume, received DLT message, key = {}", consumerRecord.key());
    String eventId = consumerRecord.key();
    if (dltService.getByKey(eventId).isPresent()) {
      logger.info("consume, DLT message already processed, skip key = {}", eventId);
      return;
    }
    // Сохраняем метаинформацию об ошибке
    String exceptionClass = null;
    String exceptionMessage = null;
    if (consumerRecord.headers() != null) {
      if (consumerRecord.headers().lastHeader("x-exception-class") != null) {
        exceptionClass =
            new String(consumerRecord.headers().lastHeader("x-exception-class").value());
      }
      if (consumerRecord.headers().lastHeader("x-exception-message") != null) {
        exceptionMessage =
            new String(consumerRecord.headers().lastHeader("x-exception-message").value());
      }
    }

    dltService.save(consumerRecord, exceptionClass, exceptionMessage);
  }
}
