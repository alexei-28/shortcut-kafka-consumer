package com.gmail.alexei28.shortcutkafkaconsumer.task3.service;

import com.gmail.alexei28.shortcutkafkaconsumer.task3.event.CreateOrderEvent;
import com.gmail.alexei28.shortcutkafkaconsumer.task3.mapper.CreateOrderEventAccountOperationMapping;
import com.gmail.alexei28.shortcutkafkaconsumer.task3.operation.AccountOperation;
import com.gmail.alexei28.shortcutkafkaconsumer.task3.repo.AccountOperationRepository;
import com.gmail.alexei28.shortcutkafkaconsumer.task3.repo.ProcessedMessageRepository;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/*
   Проблема: existsById vs Unique Constraint
   Метод processedMessageRepository.existsById(eventId) не защищает от одновременной обработки одного и того же сообщения
   двумя разными потоками (например, если Kafka сделала ребалансировку и два консьюмера получили одно сообщение).
   1. Поток А проверил existsById — false.
   2. Поток Б проверил existsById — false.
   3. Оба выполнят бизнес-логику - race Condition

   Как исправить:
   Вместо if (exists), полагайтесь на Unique Constraint в базе данных по полю event_id.
   Попробуйте просто вставить запись в processed_messages в начале или конце метода.
   Если запись с таким ID уже есть, БД выбросит DataIntegrityViolationException, и транзакция откатится.

*/
@Service
public class PaymentService {
  private final CreateOrderEventAccountOperationMapping createOrderEventAccountOperationMapping;
  private final AccountOperationRepository accountOperationRepository;
  private final ProcessedMessageRepository processedMessageRepository;
  private static final Logger logger = LoggerFactory.getLogger(PaymentService.class);

  public PaymentService(
      CreateOrderEventAccountOperationMapping createOrderEventAccountOperationMapping,
      AccountOperationRepository accountOperationRepository,
      ProcessedMessageRepository processedMessageRepository) {
    this.createOrderEventAccountOperationMapping = createOrderEventAccountOperationMapping;
    this.accountOperationRepository = accountOperationRepository;
    this.processedMessageRepository = processedMessageRepository;
  }

  @Transactional
  public void processOrderCreation(UUID eventId, CreateOrderEvent event) {
    int inserted = processedMessageRepository.insert(eventId);
    if (inserted == 0) {
      logger.warn("processOrderCreation, duplication eventId = {}", eventId);
      return; // duplicate
    }
    AccountOperation accountOperation = createOrderEventAccountOperationMapping.toOperation(event);
    accountOperationRepository.save(accountOperation);
    logger.info(
        "processOrderCreation, successfully saved to repo, accountOperation = {}",
        accountOperation);
  }
}
