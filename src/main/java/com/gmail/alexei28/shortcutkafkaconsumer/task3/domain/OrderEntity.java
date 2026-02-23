package com.gmail.alexei28.shortcutkafkaconsumer.task3.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

public class OrderEntity {
  private String externalId;
  private BigDecimal amount;
  private OrderStatus status;
  private OffsetDateTime createdAt;

  public String getExternalId() {
    return externalId;
  }

  public void setExternalId(String externalId) {
    this.externalId = externalId;
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public void setAmount(BigDecimal amount) {
    this.amount = amount;
  }

  public OrderStatus getStatus() {
    return status;
  }

  public void setStatus(OrderStatus status) {
    this.status = status;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(OffsetDateTime createdAt) {
    this.createdAt = createdAt;
  }

  @Override
  public String toString() {
    return "\nOrderEntity{"
        + "\nexternalId='"
        + externalId
        + '\''
        + ",\n amount="
        + amount
        + ",\n status="
        + status
        + ",\n createdAt="
        + createdAt
        + "\n"
        + '}';
  }
}
