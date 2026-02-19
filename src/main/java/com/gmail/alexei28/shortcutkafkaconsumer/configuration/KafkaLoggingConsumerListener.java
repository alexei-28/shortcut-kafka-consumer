package com.gmail.alexei28.shortcutkafkaconsumer.configuration;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.listener.RecordInterceptor;
import org.springframework.stereotype.Component;

@Component
public class KafkaLoggingConsumerListener implements RecordInterceptor<Object, Object> {
  private static final Logger logger = LoggerFactory.getLogger(KafkaLoggingConsumerListener.class);

  @Override
  public ConsumerRecord<Object, Object> intercept(
      ConsumerRecord<Object, Object> consumerRecord, Consumer<Object, Object> consumer) {
    // Ищем заголовок __TypeId__ среди всех заголовков записи
    consumerRecord
        .headers()
        .headers("__TypeId__")
        .forEach(
            header -> {
              String typeId = new String(header.value());
              logger.info(
                  "intercept, Topic: {} , __TypeId__: {}, partition: {}, key: {}, value: {}",
                  consumerRecord.topic(),
                  typeId,
                  consumerRecord.partition(),
                  consumerRecord.key(),
                  consumerRecord.value());
            });

    return consumerRecord; // Возвращаем запись для дальнейшей обработки в @KafkaListener
  }
}
