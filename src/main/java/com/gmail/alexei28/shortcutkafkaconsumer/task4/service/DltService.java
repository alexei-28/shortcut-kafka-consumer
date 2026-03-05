package com.gmail.alexei28.shortcutkafkaconsumer.task4.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gmail.alexei28.shortcutkafkaconsumer.task4.entity.DltMessage;
import com.gmail.alexei28.shortcutkafkaconsumer.task4.entity.DltStatus;
import com.gmail.alexei28.shortcutkafkaconsumer.task4.repo.DltMessageRepository;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DltService {
  private final ObjectMapper objectMapper;
  private final DltMessageRepository dltMessageRepository;
  private static final Logger logger = LoggerFactory.getLogger(DltService.class);

  public DltService(ObjectMapper objectMapper, DltMessageRepository dltMessageRepository) {
    this.objectMapper = objectMapper;
    this.dltMessageRepository = dltMessageRepository;
  }

  public void save(
      ConsumerRecord<String, Object> consumerRecord,
      String exceptionClass,
      String exceptionMessage) {
    Object rawValue = consumerRecord.value();
    final String payloadToSave;
    try {
      if (rawValue instanceof byte[]) {
        payloadToSave = new String((byte[]) rawValue, StandardCharsets.UTF_8);
      } else if (rawValue instanceof String) {
        payloadToSave = (String) rawValue;
      } else {
        payloadToSave = objectMapper.writeValueAsString(rawValue);
      }
    } catch (JsonProcessingException e) {
      logger.error(
          "save, Failed to serialize DLT message payload for key = {}, error: {}",
          consumerRecord.key(),
          e.getMessage());
      throw new RuntimeException("Serialization failed", e);
    }

    dltMessageRepository
        .findByMessageKey(consumerRecord.key())
        .ifPresentOrElse(
            existing -> {
              existing.setPayload(payloadToSave);
              existing.setStatus(DltStatus.NEW);
              existing.setOffsetValue(consumerRecord.offset());
              existing.setCreatedAt(OffsetDateTime.now());
              existing.setErrorClass(exceptionClass);
              existing.setErrorMessage(exceptionMessage);
              dltMessageRepository.save(existing);
            },
            () -> {
              DltMessage message =
                  new DltMessage(
                      consumerRecord.key(),
                      payloadToSave,
                      consumerRecord.topic(),
                      consumerRecord.partition(),
                      consumerRecord.offset());
              message.setErrorClass(exceptionClass);
              message.setErrorMessage(exceptionMessage);
              dltMessageRepository.save(message);
            });
  }

  public Optional<DltMessage> getByKey(String key) {
    return dltMessageRepository.findByMessageKey(key);
  }

  @Transactional
  public void deleteIfExists(String key) {
    dltMessageRepository
        .findByMessageKey(key)
        .ifPresent(
            msg -> {
              logger.info("deleteIfExists, Deleting DLT record for key = {}", key);
              dltMessageRepository.delete(msg);
            });
  }

  @Transactional
  public void markRetried(String key) {
    dltMessageRepository
        .findByMessageKey(key)
        .ifPresent(
            msg -> {
              msg.setUpdatedAt(OffsetDateTime.now());
              msg.setStatus(DltStatus.RETRIED);
              logger.info("markRetried, Marked DLT record as RETRIED for key = {}", key);
            });
  }

  @Transactional
  public void markFailed(String key) {
    dltMessageRepository
        .findByMessageKey(key)
        .ifPresent(
            msg -> {
              msg.setUpdatedAt(OffsetDateTime.now());
              msg.setStatus(DltStatus.FAILED);
              logger.info("markFailed, Marked DLT record as FAILED for key = {}", key);
            });
  }
}
