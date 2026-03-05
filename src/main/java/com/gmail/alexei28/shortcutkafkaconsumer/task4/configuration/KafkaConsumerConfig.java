package com.gmail.alexei28.shortcutkafkaconsumer.task4.configuration;

import com.gmail.alexei28.shortcutkafkaconsumer.task4.dto.UserDto;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.messaging.converter.MessageConversionException;

/*-
   Разделяем ошибки:

   | Тип ошибки                           | Retry? | DLT?                       |
   | ------------------------------------ | ------ | ---------------------------|
   | DeserializationException             | нет    | сразу                      |
   | MessageConversionException           | нет    | сразу                      |
   | IllegalArgumentException (валидация) | нет    | сразу                      |
   | Ошибки БД                            | да     | если все попытки исчерпаны |

   Если БД упала:
    Attempt 1 → ошибка
    5 секунд
    Attempt 2 → ошибка
    5 секунд
    Attempt 3 → ошибка
    → отправка в DLT

    Consumer НЕ зависнет.
    Lag не будет расти бесконечно.
    Сообщение не потеряется

    Какие исключения ловятся как "ошибка БД"
    Spring Data обычно бросает:
    - DataAccessException
    - DataIntegrityViolationException
    - DataAccessResourceFailureException
    - CannotCreateTransactionException
    Все они будут retryable по умолчанию.
*/
@Configuration
public class KafkaConsumerConfig {
  @Value("${app.kafka.groups.task4}")
  private String consumerGroup;

  private static final Logger logger = LoggerFactory.getLogger(KafkaConsumerConfig.class);

  @Bean
  public ConcurrentKafkaListenerContainerFactory<String, String> dltStringContainerFactory(
      ConsumerFactory<String, String> consumerFactory) {
    ConcurrentKafkaListenerContainerFactory<String, String> factory =
        new ConcurrentKafkaListenerContainerFactory<>();

    // Мы создаем новую копию factory, фиксируя десериализаторы на String
    factory.setConsumerFactory(consumerFactory);

    // Это гарантирует, что даже если в топике лежат байты,
    // Spring попытается интерпретировать их как String перед вызовом метода
    factory.setRecordMessageConverter(
        new org.springframework.kafka.support.converter.StringJsonMessageConverter());

    return factory;
  }

  @Bean
  public ConcurrentKafkaListenerContainerFactory<String, UserDto> kafkaListenerContainerFactory(
      ConsumerFactory<String, UserDto> consumerFactory, DefaultErrorHandler errorHandler) {
    ConcurrentKafkaListenerContainerFactory<String, UserDto> factory =
        new ConcurrentKafkaListenerContainerFactory<>();
    factory.setConsumerFactory(consumerFactory);
    factory.setCommonErrorHandler(errorHandler);
    return factory;
  }

  @Bean
  public DefaultErrorHandler errorHandler(KafkaTemplate<Object, Object> kafkaTemplate) {
    DeadLetterPublishingRecoverer deadLetterPublishingRecoverer =
        new DeadLetterPublishingRecoverer(
            kafkaTemplate,
            (consumerRecord, ex) -> {
              String originalTopic = consumerRecord.topic();
              String dltTopic =
                  originalTopic.endsWith("-dlt") ? originalTopic : originalTopic + "-dlt";
              logger.warn(
                  "defaultErrorHandler, Sending to DLT. Topic: {}, Key: {}, Offset: {}, Reason: {}",
                  dltTopic,
                  consumerRecord.key(),
                  consumerRecord.offset(),
                  ex.getMessage());
              return new TopicPartition(dltTopic, consumerRecord.partition());
            });

    // Retry-политика
    // ✅3 retry: 2s → 4s → 8s
    ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(3);
    backOff.setInitialInterval(2000L);
    backOff.setMultiplier(2.0);
    backOff.setMaxInterval(10000L);

    DefaultErrorHandler defaultErrorHandler =
        new DefaultErrorHandler(deadLetterPublishingRecoverer, backOff);
    // Retry policy (DB временная ошибка)
    defaultErrorHandler.setRetryListeners(
        (consumerRecord, ex, attempt) -> {
          logger.warn(
              "defaultErrorHandler, Retry {} for key: {}, exception:{}",
              attempt,
              consumerRecord.key(),
              ex.getClass().getSimpleName());
        });

    /*
        Эти исключения не ретраим бизнес(IllegalArgumentException) и десериализацию(DeserializationException, MessageConversionException).
        Если бросается DeserializationException, MessageConversionException, IllegalArgumentException:
        - retry НЕ происходит
        - сообщение сразу улетает в DLQ
    */
    defaultErrorHandler.addNotRetryableExceptions(
        DeserializationException.class,
        MessageConversionException.class,
        IllegalArgumentException.class);

    // Обогащение сообщения метаинформацией.
    deadLetterPublishingRecoverer.setHeadersFunction(
        (consumerRecord, ex) -> {
          Headers headers = new RecordHeaders();

          // Найти корневое бизнес-исключение
          Throwable cause = ex;
          while (cause.getCause() != null) {
            cause = cause.getCause();
          }
          headers.add("x-exception-class", cause.getClass().getName().getBytes());
          headers.add("x-exception-message", cause.getMessage().getBytes());
          String stack = getStackTrace(cause);
          // Stacktrace может быть очень большим.
          if (stack.length() > 4000) {
            stack = stack.substring(0, 4000);
          }
          headers.add("x-stacktrace", stack.getBytes());
          headers.add("x-partition", String.valueOf(consumerRecord.partition()).getBytes());
          headers.add("x-key", consumerRecord.key().toString().getBytes());
          headers.add("x-original-offset", String.valueOf(consumerRecord.offset()).getBytes());
          headers.add("x-failed-at", Instant.now().toString().getBytes());
          return headers;
        });
    return defaultErrorHandler;
  }

  private String getStackTrace(Throwable throwable) {
    StringWriter sw = new StringWriter();
    PrintWriter pw = new PrintWriter(sw);
    throwable.printStackTrace(pw);
    return sw.toString();
  }
}
