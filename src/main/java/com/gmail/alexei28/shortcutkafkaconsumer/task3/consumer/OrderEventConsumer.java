package com.gmail.alexei28.shortcutkafkaconsumer.task3.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gmail.alexei28.shortcutkafkaconsumer.task3.event.CreateOrderEvent;
import com.gmail.alexei28.shortcutkafkaconsumer.task3.service.PaymentService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/*
  - Решение
    Transactional Outbox (at-least-once) + Idempotent Consumer (exactly-once на бизнес-уровне)
    -на стороне producer-a
     - Гарантия at-least-once
    -на стороне consumer-a
     - Гарантия at-least-once на уровне инфраструктуры(Kafka) и exactly-once на уровне бизнес-логики.

    Consumer + processed_event (PK)
    - дубликат безопасен
    - consumer проигнорирует повтор

   -Почему безопасно?
    Если processOrderCreation() кинет exception:
     -транзакция откатится
     -ack не вызовется
     -Kafka сделает retry

    Если метод вернулся успешно:
     - commit уже произошёл
     - можно безопасно вызвать ack

   -Правило идемпотентного consumer-a:
    проверка дубликата, бизнес-логика и сохранение ID события должны происходить в рамках ОДНОЙ транзакции БД.

    Вывод:
    Код реализует Exactly-once семантику на уровне бизнеса.
    Даже если Kafka доставит сообщение дважды (at-least-once), база данных примет изменения только один раз.
    Обеспечивает гарантию at-least-once  на уровне инфраструктуры и exactly-once на уровне бизнес-логики.

   -Логика событий:
     1. Если БД упала или возникла ошибка: Метод processOrderCreation выбрасывает исключение.
     2. Прерывание потока: Исключение пробрасывается в метод consume. Строка ack.acknowledge() никогда не вызывается.
     3. Результат в Kafka: Офсет не зафиксирован. Kafka считает, что сообщение не обработано.
     4. Retry (Повтор): Через некоторое время (или после перезапуска приложения) Kafka снова отдаст
        это же сообщение этому или другому потребителю.
     5. Восстановление: Когда БД оживет, сообщение будет обработано успешно, транзакция закоммитится,
        и только тогда вызовется ack.acknowledge().

    -Алгоритм:
        consume()
           ↓
        processOrderCreation()  ← @Transactional (proxy)
           ↓
        COMMIT или ROLLBACK
           ↓
        возврат в consume()
           ↓
        ack.acknowledge()

     - Как работает read_committed?
       Когда вы включаете этот уровень изоляции:
      - Consumer игнорирует сообщения, транзакции которых были отменены (Aborted).
      - Consumer «притормаживает» чтение на границе последней открытой транзакции (LSO — Last Stable Offset).
        Он не отдаст вам сообщение, пока Продюсер не пришлет специальный маркер Commit.

      - Почему это важно для связки Transactional Outbox + Idempotent Consumer?
        - Outbox на стороне отправителя гарантирует, что сообщение в Kafka попадет только если запись в БД прошла успешно.
        - Если компонент-отправитель (Relay) использует транзакции Kafka для надежности,
          то без read_committed на стороне получателя мы рискуем обработать данные, которые технически считаются «мусором» или «откатом».

*/
@Component
public class OrderEventConsumer {
  private final PaymentService paymentService;
  private final ObjectMapper mapper;
  private static final Logger logger = LoggerFactory.getLogger(OrderEventConsumer.class);

  public OrderEventConsumer(PaymentService paymentService, ObjectMapper mapper) {
    this.paymentService = paymentService;
    this.mapper = mapper;
  }

  /*
    Example of consumerRecord.value - CreateOrderEvent(json):
    {
      "amount": 1250.5,
      "eventId": "3d10b49f-3bb7-467e-ab33-8973458194b2",
      "orderId": "38041127-5fd4-4718-bbbe-17b28eb6cdbf",
      "currency": "EUR",
      "externalId": "cd311b61-9ae0-4ac9-a39e-44cbf9456e1f",
      "senderIBAN": "DE89370400440532013000",
      "receiverIBAN": "GB29NWBK60161331926819"
    }
  */
  @KafkaListener(topics = "${app.kafka.topics.task3}", groupId = "${app.kafka.groups.task3}")
  public void consume(
      @Header(KafkaHeaders.RECEIVED_KEY) String key,
      ConsumerRecord<String, String> consumerRecord,
      Acknowledgment ack)
      throws Exception {
    logger.info("consume, key = {}, consumerRecord = {}", key, consumerRecord);
    // business logic
    CreateOrderEvent event = mapper.readValue(consumerRecord.value(), CreateOrderEvent.class);
    paymentService.processOrderCreation(event.eventId(), event);

    // ACK только если метод processOrderCreation завершился без исключений.
    ack.acknowledge();
  }
}
