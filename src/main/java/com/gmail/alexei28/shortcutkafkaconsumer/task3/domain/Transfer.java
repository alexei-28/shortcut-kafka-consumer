package com.gmail.alexei28.shortcutkafkaconsumer.task3.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

// JPA Entity — состояние в БД
@Entity
@Table(
    name = "transfers",
    indexes = {
      @Index(name = "idx_transfer_operation_id", columnList = "operation_id", unique = true),
      @Index(name = "idx_transfer_status", columnList = "status")
    })
public class Transfer {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  // внешний id операции (идемпотентность бизнеса, идемпотентность операции)
  @Column(name = "operation_id", nullable = false, unique = true, updatable = false)
  private UUID operationId;

  @Column(name = "sender_iban", nullable = false, length = 34)
  private String senderIban;

  @Column(name = "receiver_iban", nullable = false, length = 34)
  private String receiverIban;

  @Column(name = "receiver_bic", nullable = false, length = 11)
  private String receiverBic;

  @Column(nullable = false, precision = 19, scale = 4)
  private BigDecimal amount;

  @Column(nullable = false, length = 3)
  private String currency;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private TransferStatus status;

  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @Column(name = "processed_at")
  private OffsetDateTime processedAt;

  protected Transfer() {
    // JPA
  }

  public Transfer(
      UUID operationId,
      String senderIban,
      String receiverIban,
      String receiverBic,
      BigDecimal amount,
      String currency) {
    this.operationId = operationId;
    this.senderIban = senderIban;
    this.receiverIban = receiverIban;
    this.receiverBic = receiverBic;
    this.amount = amount;
    this.currency = currency;
    this.status = TransferStatus.NEW;
    this.createdAt = OffsetDateTime.now();
  }

  // getters
  public UUID getId() {
    return id;
  }

  public UUID getOperationId() {
    return operationId;
  }

  public String getSenderIban() {
    return senderIban;
  }

  public String getReceiverIban() {
    return receiverIban;
  }

  public String getReceiverBic() {
    return receiverBic;
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public String getCurrency() {
    return currency;
  }

  public TransferStatus getStatus() {
    return status;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }

  public OffsetDateTime getProcessedAt() {
    return processedAt;
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) return false;
    Transfer transfer = (Transfer) o;
    return Objects.equals(id, transfer.id);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }

  @Override
  public String toString() {
    return "Transfer{"
        + "id="
        + id
        + ", operationId="
        + operationId
        + ", senderIban='"
        + senderIban
        + '\''
        + ", receiverIban='"
        + receiverIban
        + '\''
        + ", receiverBic='"
        + receiverBic
        + '\''
        + ", amount="
        + amount
        + ", currency='"
        + currency
        + '\''
        + ", status="
        + status
        + ", createdAt="
        + createdAt
        + ", processedAt="
        + processedAt
        + '}';
  }
}
