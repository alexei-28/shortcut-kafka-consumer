package com.gmail.alexei28.shortcutkafkaconsumer.task3.consumer;

import com.gmail.alexei28.shortcutkafkaconsumer.task3.domain.Transfer;
import com.gmail.alexei28.shortcutkafkaconsumer.task3.dto.TransferDto;
import com.gmail.alexei28.shortcutkafkaconsumer.task3.interfaces.TransferMapper;
import com.gmail.alexei28.shortcutkafkaconsumer.task3.repository.TransferRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/*
 Задача 3 — «Перевод через СБП»
   Сервис обрабатывает межбанковские переводы.
   Клиент инициировал перевод -> списание со счёта -> результат в следующий сервис по цепочке.
   Что говорит бизнес:
   Двойное списание — P1, штраф от регулятора
   Потеря перевода — P1, клиент без денег
   Нужен полный аудит каждой операции
   Стек: PostgreSQL + Kafka
   Задание: реализуй полный цикл обработки. Ни потеря, ни дубль недопустимы — выбери подход и обоснуй.

   Решение:
   Kafka EOS (Exactly-Once Semantics) гарантирует только:
   - продюсер не запишет дубликат в Kafka topic и consumer не обработает один offset дважды внутри Kafka transaction.

   А нам нужно:
   Kafka  ->  Consumer  ->  PostgreSQL  ->  деньги клиента
*/

@Service
public class TransferConsumer {
  private final TransferRepository repository;
  private final TransferMapper mapper;
  private static final Logger logger = LoggerFactory.getLogger(TransferConsumer.class);

  public TransferConsumer(TransferMapper mapper, TransferRepository repository) {
    this.mapper = mapper;
    this.repository = repository;
  }

  @Transactional
  @KafkaListener(topics = "${app.kafka.topics.task3}", groupId = "${app.kafka.groups.task3}")
  public void consume(TransferDto transferDto, Acknowledgment ack) {
    logger.info("consume, processing transferDto: {}", transferDto);
    Transfer transfer = mapper.toEntity(transferDto);
    process(transfer);
  }

  private void process(Transfer transfer) {
    logger.info("process, transfer:  {}", transfer);
    // repository.save(transfer);
    logger.info("process, successfully saved to database, transfer: {}", transfer);
  }
}
