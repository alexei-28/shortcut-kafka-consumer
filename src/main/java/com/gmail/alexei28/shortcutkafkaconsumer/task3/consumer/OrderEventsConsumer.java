package com.gmail.alexei28.shortcutkafkaconsumer.task3.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gmail.alexei28.shortcutkafkaconsumer.task3.domain.OrderEntity;
import com.gmail.alexei28.shortcutkafkaconsumer.task3.service.PaymentService;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/*
 */
@Component
public class OrderEventsConsumer {
  private final PaymentService paymentService;
  private final ObjectMapper mapper;
  private static final Logger logger = LoggerFactory.getLogger(OrderEventsConsumer.class);

  public OrderEventsConsumer(PaymentService paymentService, ObjectMapper mapper) {
    this.paymentService = paymentService;
    this.mapper = mapper;
  }

  @KafkaListener(topics = "${app.kafka.topics.task3}", groupId = "${app.kafka.groups.task3}")
  public void consume(ConsumerRecord<String, String> consumerRecord, Acknowledgment ack) {
    try {
      logger.info("consume, consumerRecord = {}", consumerRecord);
      OrderEntity orderEntity = mapper.readValue(consumerRecord.value(), OrderEntity.class);
      logger.info("consume, orderEntity = {}", orderEntity);
      // ВАЖНО: eventId из payload
      paymentService.handleOrderCreated(UUID.fromString(orderEntity.getExternalId()), orderEntity);
      // commit offset ТОЛЬКО после commit DB
      ack.acknowledge();
    } catch (Exception e) {
      logger.error("consume, Failed to process record = {}", consumerRecord.offset(), e);
      // offset НЕ коммитится → Kafka пришлёт снова
    }
  }
}
