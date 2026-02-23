package com.gmail.alexei28.shortcutkafkaconsumer.task3.operation;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Objects;
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

  @Column(name = "currency", nullable = false, length = 3)
  private String currency;

  @Column(name = "sender_iban", nullable = false, length = 34)
  private String senderIBAN;

  @Column(name = "receiver_iban", nullable = false, length = 34)
  private String receiverIBAN;

  @Column(nullable = false)
  private OffsetDateTime createdAt;

  public AccountOperation() {}

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

  public String getCurrency() {
    return currency;
  }

  public void setCurrency(String currency) {
    this.currency = currency;
  }

  public String getSenderIBAN() {
    return senderIBAN;
  }

  public void setSenderIBAN(String senderIBAN) {
    this.senderIBAN = senderIBAN;
  }

  public String getReceiverIBAN() {
    return receiverIBAN;
  }

  public void setReceiverIBAN(String receiverIBAN) {
    this.receiverIBAN = receiverIBAN;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof AccountOperation that)) return false;
    return Objects.equals(externalId, that.externalId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(externalId);
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
        + ",\n currency='"
        + currency
        + '\''
        + ",\n senderIBAN='"
        + senderIBAN
        + '\''
        + ",\n receiverIBAN='"
        + receiverIBAN
        + '\''
        + ",\n createdAt="
        + createdAt
        + '}';
  }
}
