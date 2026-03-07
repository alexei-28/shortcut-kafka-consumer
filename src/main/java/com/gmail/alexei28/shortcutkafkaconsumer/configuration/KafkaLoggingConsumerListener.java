package com.gmail.alexei28.shortcutkafkaconsumer.configuration;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.listener.RecordInterceptor;
import org.springframework.stereotype.Component;

@Component
public class KafkaLoggingConsumerListener<K, V> implements RecordInterceptor<K, V> {

  private static final Logger logger = LoggerFactory.getLogger(KafkaLoggingConsumerListener.class);

  @Override
  public ConsumerRecord<K, V> intercept(ConsumerRecord<K, V> record, Consumer<K, V> consumer) {

    record
        .headers()
        .headers("__TypeId__")
        .forEach(
            header -> {
              String typeId = new String(header.value());
              logger.info(
                  "intercept, Topic: {}, __TypeId__: {}, partition: {}, key: {}, value: {}",
                  record.topic(),
                  typeId,
                  record.partition(),
                  record.key(),
                  record.value());
            });

    return record;
  }
}
