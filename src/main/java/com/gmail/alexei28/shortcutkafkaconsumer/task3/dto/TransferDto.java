package com.gmail.alexei28.shortcutkafkaconsumer.task3.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/*-
    Kafka DTO (Event) — immutable сообщение (никаких Entity в Kafka).
    Никогда не отправляй JPA Entity в Kafka.
    Важно:
        operationId защищает создание операции, но не защищает обработку события.
     Т.е. в рамках одного и того же operationId может быть несколько eventId.

    Итог
      operationId → защита от двойного создания платежа
      eventId → защита от двойной обработки Kafka-сообщения

      Они не взаимозаменяемы.
      Без operationId → дубли платежей
      Без eventId → дубли списаний

      В банковских системах нужны оба одновременно.
*/
public record TransferDto(
    // конкретное сообщение в Kafka(идентификатор сообщения Kafka) - идемпотентность сообщения
    // (Kafka-идемпотентность)
    UUID eventId,
    // банковская операция - идемпотентность бизнеса (идемпотентность операции)
    UUID operationId,
    String senderIban,
    String receiverIban,
    String receiverBic,
    BigDecimal amount,
    String currency,
    OffsetDateTime createdAt) {

  public TransferDto {
    if (amount != null) {
      amount = amount.stripTrailingZeros();
    }
  }
}
