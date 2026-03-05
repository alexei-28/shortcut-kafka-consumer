package com.gmail.alexei28.shortcutkafkaconsumer.task5.service;

import com.gmail.alexei28.shortcutkafkaconsumer.task5.entity.DlqStatus;
import com.gmail.alexei28.shortcutkafkaconsumer.task5.repo.DlqMessageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/*
    В Spring это не работает корректно, потому что транзакции применяются через прокси, а вызов через this не проходит через прокси.
    Поэтому аннотации @Transactional для markProcessed и markFailed игнорируются в классе DltService.
    Решение - вынести эти методы в отдельный сервис DltMessageUpdater, который будет вызываться из DltService.
*/
@Service
public class DlqMessageUpdater {

  private final DlqMessageRepository dlqMessageRepository;

  public DlqMessageUpdater(DlqMessageRepository dlqMessageRepository) {
    this.dlqMessageRepository = dlqMessageRepository;
  }

  @Transactional
  public void markRetried(String key) {
    dlqMessageRepository
        .findByEventId(key)
        .ifPresent(
            msg -> {
              msg.setStatus(DlqStatus.RETRIED);
              msg.setRetryCount(msg.getRetryCount() + 1);
            });
  }

  @Transactional
  public void markFailed(String key, String errorMessage) {
    dlqMessageRepository
        .findByEventId(key)
        .ifPresent(
            msg -> {
              msg.setStatus(DlqStatus.FAILED);
              msg.setErrorMessage(errorMessage);
              msg.setRetryCount(msg.getRetryCount() + 1);
            });
  }

  @Transactional
  public void markExhausted(String key, String errorMessage) {
    dlqMessageRepository
        .findByEventId(key)
        .ifPresent(
            msg -> {
              msg.setStatus(DlqStatus.EXHAUSTED);
              msg.setErrorMessage(errorMessage);
            });
  }

  @Transactional
  public void deleteIfExists(String key) {
    dlqMessageRepository.findByEventId(key).ifPresent(dlqMessageRepository::delete);
  }
}
