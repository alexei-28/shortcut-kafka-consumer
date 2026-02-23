package com.gmail.alexei28.shortcutkafkaconsumer.task3.event;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateOrderEvent(
    UUID eventId,
    UUID orderId,
    String externalId,
    BigDecimal amount,
    String currency,
    String senderIBAN,
    String receiverIBAN) {
  // for correct validate hashcode/equals
  public CreateOrderEvent {
    if (amount != null) {
      amount = amount.stripTrailingZeros();
    }
  }

  @Override
  public String toString() {
    return "\nCreateOrderEvent{"
        + "\neventId="
        + eventId
        + ",\n orderId="
        + orderId
        + ",\n externalId='"
        + externalId
        + ",\n amount="
        + amount
        + ",\n currency='"
        + currency
        + ",\n senderIBAN='"
        + senderIBAN
        + ",\n receiverIBAN='"
        + receiverIBAN
        + "\n"
        + '}';
  }
}
