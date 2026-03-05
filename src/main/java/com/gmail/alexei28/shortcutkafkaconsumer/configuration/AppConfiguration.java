package com.gmail.alexei28.shortcutkafkaconsumer.configuration;

import com.gmail.alexei28.shortcutkafkaconsumer.task4.dto.UserDto;
import org.springframework.beans.factory.annotation.Qualifier;
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

  public AppConfiguration(
      KafkaLoggingConsumerListener<String, UserDto> kafkaLoggingConsumerListenerUserDto,
      @Qualifier("kafkaListenerContainerFactory") ConcurrentKafkaListenerContainerFactory<String, UserDto> factory) {
    factory.setRecordInterceptor(kafkaLoggingConsumerListenerUserDto);
  }
}
