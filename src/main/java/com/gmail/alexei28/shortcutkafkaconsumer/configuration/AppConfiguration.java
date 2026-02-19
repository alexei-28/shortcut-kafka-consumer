package com.gmail.alexei28.shortcutkafkaconsumer.configuration;

import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;

/*
   KafkaLoggingConsumerListener- почему это лучшее решение?
   -DRY (Don't Repeat Yourself): Вам не нужно добавлять @Header("__TypeId__") в каждый метод consume.
   -Сквозная логика: Это работает для всех топиков и всех DTO автоматически.
   -Чистота бизнес-логики: Ваш ConsumerTask1 остается чистым и сфокусированным только на сохранении данных в БД и реализации стратегии At-most-once.
*/
@Configuration
public class AppConfiguration {
  private final KafkaLoggingConsumerListener kafkaLoggingConsumerListener;

  // Spring Boot автоматически создает эту фабрику, мы просто её настраиваем
  public AppConfiguration(
      KafkaLoggingConsumerListener kafkaLoggingConsumerListener,
      ConcurrentKafkaListenerContainerFactory<Object, Object> factory) {
    this.kafkaLoggingConsumerListener = kafkaLoggingConsumerListener;
    factory.setRecordInterceptor(kafkaLoggingConsumerListener);
  }
}
