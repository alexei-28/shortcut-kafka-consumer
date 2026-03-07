package com.gmail.alexei28.shortcutkafkaconsumer.task5.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gmail.alexei28.shortcutkafkaconsumer.task5.dto.UserDto;
import com.gmail.alexei28.shortcutkafkaconsumer.task5.entity.DlqMessage;
import com.gmail.alexei28.shortcutkafkaconsumer.task5.entity.DlqStatus;
import com.gmail.alexei28.shortcutkafkaconsumer.task5.repo.DlqMessageRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.messaging.handler.annotation.support.MethodArgumentNotValidException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DlqService {
  @Value("${app.kafka.topics.task5}")
  private String mainTopic;

  @Value("${app.kafka.retry.max-attempts:10}")
  private int maxAttempts;

  private final DlqMessageUpdater dlqMessageUpdater;
  private final ObjectMapper objectMapper;
  private final DlqMessageRepository dlqMessageRepository;
  private final KafkaTemplate<String, UserDto> kafkaTemplate;
  private static final Logger logger = LoggerFactory.getLogger(DlqService.class);

  public DlqService(
      DlqMessageUpdater dlqMessageUpdater,
      ObjectMapper objectMapper,
      DlqMessageRepository dlqMessageRepository,
      KafkaTemplate<String, UserDto> kafkaTemplate) {
    this.dlqMessageUpdater = dlqMessageUpdater;
    this.objectMapper = objectMapper;
    this.dlqMessageRepository = dlqMessageRepository;
    this.kafkaTemplate = kafkaTemplate;
  }

  @Transactional
  public void saveForDeserialization(
      String key,
      String topic,
      Integer partitionNumber,
      Long offset,
      String payload,
      String exceptionClass,
      String exceptionMessage) {
    DlqMessage message = new DlqMessage();
    message.setEventId(key);
    message.setTopic(topic);
    message.setPartitionNumber(partitionNumber);
    message.setOffsetValue(offset);
    message.setPayload(payload);
    message.setStatus(DlqStatus.NEW);
    message.setErrorClass(exceptionClass);
    message.setErrorMessage(exceptionMessage);
    dlqMessageRepository.save(message);
    logger.info(
        "saveForDeserialization, saved DLT message with key = {}, payload = {}", key, payload);
  }

  @Transactional
  public void save(
      ConsumerRecord<?, ?> consumerRecord, String exceptionClass, String exceptionMessage) {
    String key = String.valueOf(consumerRecord.key());
    Optional<DlqMessage> dltMessageOpt = getByKey(key);
    if (dltMessageOpt.isPresent()) {
      logger.info("save, DLT message with key = {} already exists, skipping save", key);
      return;
    }
    try {
      String payload = objectMapper.writeValueAsString(consumerRecord.value());
      DlqMessage dlqMessage =
          new DlqMessage(
              key,
              payload,
              consumerRecord.topic(),
              consumerRecord.partition(),
              consumerRecord.offset());
      dlqMessage.setPayload(payload);
      dlqMessage.setStatus(DlqStatus.NEW);
      dlqMessage.setOffsetValue(consumerRecord.offset());
      dlqMessage.setErrorClass(exceptionClass);
      dlqMessage.setErrorMessage(exceptionMessage);
      dlqMessageRepository.save(dlqMessage);
      logger.info("save, DLT message saved: key = {}, payload = {}", key, payload);
    } catch (JsonProcessingException e) {
      logger.error(
          "save, Failed to serialize DLT message payload for key = {}, error: {}",
          consumerRecord.key(),
          e.getMessage());
      throw new RuntimeException("Serialization failed", e);
    }
  }

  @Transactional
  public void retry(DlqMessage dlqMessage) {
    logger.info("retry, retrying message with key: {}", dlqMessage.getEventId());
    processedMessage(dlqMessage);
  }

  @Transactional
  public void retryAll(List<DlqMessage> dlqMessageList) {
    logger.info("retryAll, retrying {} messages", dlqMessageList.size());
    for (DlqMessage dlqMessage : dlqMessageList) {
      processedMessage(dlqMessage);
    }
  }

  private void processedMessage(DlqMessage dlqMessage) {
    logger.info("processedMessage, key: {}", dlqMessage.getEventId());
    // 1. Проверка на максимальное кол-во попыток (ПЕРЕД выполнением работы)
    if (dlqMessage.getRetryCount() >= maxAttempts) {
      logger.warn(
          "processedMessage, Key: {} reached max attempts ({})",
          dlqMessage.getEventId(),
          maxAttempts);
      dlqMessageUpdater.markExhausted(dlqMessage.getEventId(), "Max attempts reached");
      return;
    }

    // 2. Проверка на фатальную ошибку
    if (isPermanentError(dlqMessage.getErrorClass())) {
      dlqMessageUpdater.markExhausted(dlqMessage.getEventId(), dlqMessage.getErrorMessage());
      return;
    }
    try {
      UserDto dto = objectMapper.readValue(dlqMessage.getPayload(), UserDto.class);
      kafkaTemplate.send(mainTopic, dlqMessage.getEventId(), dto);
      // ТОЛЬКО ЕСЛИ ОТПРАВКА УСПЕШНА — Увеличиваем счетчик и ставим статус RETRIED
      dlqMessageUpdater.markRetried(dlqMessage.getEventId());
      logger.info("processedMessage, Successfully retried key: {}", dlqMessage.getEventId());
    } catch (JsonProcessingException e) {
      // Защита "на лету". Если вдруг был пустой errorClass, но readValue всё равно упал.
      // Если упало здесь — значит данные в базе "битые", это навсегда
      dlqMessageUpdater.markExhausted(
          dlqMessage.getEventId(), "Payload corruption: " + e.getMessage());
    } catch (Exception ex) {
      // Здесь мы обрабатываем только временные(transient) ошибки (недоступна Kafka, сеть, БД и
      // т.д.) - e.g. java.net.ConnectException.
      // Увеличиваем счетчик и ставим статус FAILED.
      dlqMessageUpdater.markFailed(dlqMessage.getEventId(), ex.getMessage());
    }
  }

  /*
    Определяет, является ли ошибка неустранимой без вмешательства человека
  */
  private boolean isPermanentError(String errorClass) {
    if (errorClass == null) return false;
    return errorClass.contains(IllegalArgumentException.class.getSimpleName())
        || errorClass.contains(JsonProcessingException.class.getSimpleName())
        || errorClass.contains(MethodArgumentNotValidException.class.getSimpleName())
        || errorClass.contains(DeserializationException.class.getSimpleName());
  }

  public Optional<DlqMessage> getByKey(String key) {
    return dlqMessageRepository.findByEventId(key);
  }

  public List<DlqMessage> findByFilter(
      String topic, List<DlqStatus> statuses, OffsetDateTime from, OffsetDateTime to) {
    return dlqMessageRepository.findAll().stream()
        .filter(msg -> topic == null || topic.equals(msg.getTopic()))
        .filter(msg -> statuses == null || statuses.isEmpty() || statuses.contains(msg.getStatus()))
        .filter(msg -> from == null || !msg.getCreatedAt().isBefore(from))
        .filter(msg -> to == null || !msg.getCreatedAt().isAfter(to))
        .toList();
  }

  @Transactional
  public void markIgnored(String key) {
    dlqMessageRepository
        .findByEventId(key)
        .ifPresent(
            msg -> {
              msg.setStatus(DlqStatus.IGNORED);
            });
  }
}
