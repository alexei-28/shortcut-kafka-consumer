package com.gmail.alexei28.shortcutkafkaconsumer.task5.configuration;

import com.gmail.alexei28.shortcutkafkaconsumer.task5.service.DlqService;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.DeserializationException;

/*
   Spring Kafka автоматически ищет bean типа DefaultErrorHandler.
   DefaultErrorHandler — стандартный обработчик ошибок из Spring Kafka.
   Он управляет повторными попытками обработки сообщения и отправкой в DLT (Dead Letter Topic, то есть «топик мёртвых писем»)
   при исчерпании попыток.
   Когда создаётся контейнер для @KafkaListener, происходит:
       ConcurrentKafkaListenerContainerFactory
           ↓
       ContainerProperties
           ↓
       setCommonErrorHandler(DefaultErrorHandler)

       В итоге pipeline выглядит так:
       Kafka Broker
             ↓
       KafkaConsumer.poll()
             ↓
       Spring Kafka Listener Container
             ↓
       @KafkaListener
             ↓
       Exception
             ↓
       DefaultErrorHandler
             ↓
       DlqService.save()
             ↓
       PostgreSQL DLQ table
*/
@Configuration
public class KafkaConsumerConfig {
  private static final Logger logger = LoggerFactory.getLogger(KafkaConsumerConfig.class);

  // Обработка ошибок Kafka
  @Bean
  public DefaultErrorHandler errorHandler(DlqService dlqService) {
    return new DefaultErrorHandler(
        (consumerRecord, ex) -> {
          String key = "unknown";
          String topic = "unknown";
          String payload = "null";
          Long offset = null;
          Integer partitionNumber = null;

          // Пытаемся достать метаданные из record (даже если десериализация упала, record может
          // быть не null)
          if (consumerRecord != null) {
            topic = consumerRecord.topic();
            key = consumerRecord.key() != null ? consumerRecord.key().toString() : "null";
            offset = consumerRecord.offset();
            partitionNumber = consumerRecord.partition();
          }
          Throwable cause = ex instanceof DeserializationException ? ex : ex.getCause();
          if (cause instanceof DeserializationException de) {
            // E.g. json is invalid.
            byte[] data = de.getData(); // Извлекаем "битое" тело сообщения
            payload = data != null ? new String(data, StandardCharsets.UTF_8) : "null";
            logger.error(
                "errorHandler, Deserialization failed! Topic: {}, Key: {}, Payload: {}",
                topic,
                key,
                payload);
            dlqService.saveForDeserialization(
                key,
                topic,
                partitionNumber,
                offset,
                payload,
                cause.getClass().getName(),
                cause.getMessage());
          } else if (consumerRecord != null) {
            // Обычная ошибка обработки (бизнес-логика, e.g.IllegalArgumentException)
            logger.error(
                "errorHandler, Processing failed! Topic: {}, Key: {}, error message: {}",
                topic,
                key,
                cause.getMessage());
            dlqService.save(consumerRecord, cause.getClass().getName(), cause.getMessage());
          }
        });
  }
}
