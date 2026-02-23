package com.gmail.alexei28.shortcutkafkaconsumer.task2.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

public class CashbackDto {
  private String eventId; // idempotency key
  private Long userId;
  private String orderId;
  private BigDecimal amount;
  private String status;
  private Double percentage;
  private LocalDateTime createdAt;
  private LocalDateTime appliedAt;
  private LocalDateTime receivedAt;

  public CashbackDto() {}

  public String getEventId() {
    return eventId;
  }

  public void setEventId(String eventId) {
    this.eventId = eventId;
  }

  public Long getUserId() {
    return userId;
  }

  public void setUserId(Long userId) {
    this.userId = userId;
  }

  public String getOrderId() {
    return orderId;
  }

  public void setOrderId(String orderId) {
    this.orderId = orderId;
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public void setAmount(BigDecimal amount) {
    this.amount = amount;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public Double getPercentage() {
    return percentage;
  }

  public void setPercentage(Double percentage) {
    this.percentage = percentage;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(LocalDateTime createdAt) {
    this.createdAt = createdAt;
  }

  public LocalDateTime getAppliedAt() {
    return appliedAt;
  }

  public void setAppliedAt(LocalDateTime appliedAt) {
    this.appliedAt = appliedAt;
  }

  public LocalDateTime getReceivedAt() {
    return receivedAt;
  }

  public void setReceivedAt(LocalDateTime receivedAt) {
    this.receivedAt = receivedAt;
  }

  /*
     Для BigDecimal значения 0 и 0.0 не равны при использовании метода .equals(),
     так как у них разный scale (масштаб/количество знаков после запятой). Однако с точки зрения бизнеса — это одна и та же сумма.
     Чтобы объект вел себя предсказуемо в бизнес-логике нужно, чтобы он сравнивал amount через compareTo.
     E.g.
     1.0 vs 1.00
     .equals() -> false
     .compareTo() -> true
  */
  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    CashbackDto that = (CashbackDto) o;

    // Используем compareTo == 0 для BigDecimal вместо Objects.equals
    boolean amountsEqual =
        (amount == null && that.amount == null)
            || (amount != null && that.amount != null && amount.compareTo(that.amount) == 0);

    return amountsEqual
        && Objects.equals(eventId, that.eventId)
        && Objects.equals(userId, that.userId)
        && Objects.equals(orderId, that.orderId)
        && Objects.equals(status, that.status)
        && Objects.equals(percentage, that.percentage)
        && Objects.equals(createdAt, that.createdAt)
        && Objects.equals(appliedAt, that.appliedAt)
        && Objects.equals(receivedAt, that.receivedAt);
  }

  @Override
  public int hashCode() {
    // Внимание: при использовании compareTo в equals, hashCode
    // должен быть консистентным. Обычно BigDecimal нормализуют:
    return Objects.hash(
        eventId,
        userId,
        orderId,
        amount != null ? amount.stripTrailingZeros() : null,
        status,
        percentage,
        createdAt,
        appliedAt,
        receivedAt);
  }

  @Override
  public String toString() {
    return "CashbackDto{"
        + "eventId='"
        + eventId
        + '\''
        + ", userId="
        + userId
        + ", orderId='"
        + orderId
        + '\''
        + ", amount="
        + amount
        + ", status='"
        + status
        + '\''
        + ", percentage="
        + percentage
        + ", createdAt="
        + createdAt
        + ", appliedAt="
        + appliedAt
        + ", receivedAt="
        + receivedAt
        + '}';
  }
}
