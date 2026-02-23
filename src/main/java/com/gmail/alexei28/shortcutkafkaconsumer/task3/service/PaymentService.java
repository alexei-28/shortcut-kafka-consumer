package com.gmail.alexei28.shortcutkafkaconsumer.task3.service;

import com.gmail.alexei28.shortcutkafkaconsumer.task3.domain.AccountOperation;
import com.gmail.alexei28.shortcutkafkaconsumer.task3.domain.OrderEntity;
import com.gmail.alexei28.shortcutkafkaconsumer.task3.idempotency.ProcessedMessageEntity;
import com.gmail.alexei28.shortcutkafkaconsumer.task3.repository.AccountOperationRepository;
import com.gmail.alexei28.shortcutkafkaconsumer.task3.repository.ProcessedMessageRepository;
import jakarta.transaction.Transactional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class PaymentService {
  private final ProcessedMessageRepository processedRepo;
  private final AccountOperationRepository operationRepository;
  private static final Logger logger = LoggerFactory.getLogger(PaymentService.class);

  public PaymentService(
      ProcessedMessageRepository processedRepo, AccountOperationRepository operationRepository) {
    this.processedRepo = processedRepo;
    this.operationRepository = operationRepository;
  }

  @Transactional
  public void handleOrderCreated(UUID externalId, OrderEntity orderEntity) {
    logger.info("handleOrderCreated, externalId = {}, orderEntity = {}", externalId, orderEntity);
    // 1. проверяем — уже обрабатывали?
    if (processedRepo.existsByEventId(externalId)) {
      return;
    }

    // 2. БИЗНЕС-ОПЕРАЦИЯ (например списание)
    AccountOperation accountOperation =
        new AccountOperation(orderEntity.getExternalId(), orderEntity.getAmount());
    operationRepository.save(accountOperation);
    logger.info(
        "handleOrderCreated, successfully saved to repo accountOperation = {}", accountOperation);

    // 3. помечаем сообщение обработанным
    ProcessedMessageEntity processedMessageEntity = new ProcessedMessageEntity();
    processedMessageEntity.setEventId(externalId);
    processedRepo.save(processedMessageEntity);
    logger.info(
        "handleOrderCreated, successfully saved to repo, processedMessageEntity = {}",
        processedMessageEntity);
  }
}
