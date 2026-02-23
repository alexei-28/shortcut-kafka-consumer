package com.gmail.alexei28.shortcutkafkaconsumer.task3.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "account_operations")
public class AccountOperation {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  // externalId (из заказа) используем как ключ идемпотентности
  @Column(nullable = false, unique = true)
  private String externalId;

  @Column(nullable = false)
  private BigDecimal amount;

  @Column(nullable = false)
  private OffsetDateTime createdAt = OffsetDateTime.now();

  public AccountOperation() {}

  public AccountOperation(String externalId, BigDecimal amount) {
    this.externalId = externalId;
    this.amount = amount;
    this.createdAt = OffsetDateTime.now();
  }

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

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

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(OffsetDateTime createdAt) {
    this.createdAt = createdAt;
  }

  @Override
  public String toString() {
    return "\nAccountOperation{"
        + "\nid="
        + id
        + ",\n externalId='"
        + externalId
        + '\''
        + ",\n amount="
        + amount
        + ",\n createdAt="
        + createdAt
        + "\n"
        + '}';
  }
}
