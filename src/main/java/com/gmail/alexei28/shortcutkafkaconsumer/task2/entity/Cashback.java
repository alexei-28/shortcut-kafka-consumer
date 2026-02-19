package com.gmail.alexei28.shortcutkafkaconsumer.task2.entity;

import jakarta.persistence.*;
import jakarta.persistence.Entity;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(
    name = "cashbacks",
    uniqueConstraints = @UniqueConstraint(name = "uk_cashback_event_id", columnNames = "event_id"))
public class Cashback {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "event_id", nullable = false, updatable = false)
  private String eventId;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(name = "order_id", unique = true, nullable = false)
  private String orderId;

  @Column(nullable = false)
  private BigDecimal amount; // Сумма кэшбэка

  @Column(nullable = false)
  private Double percentage; // Процент начисления (напр. 0.05 для 5%)

  @Enumerated(EnumType.STRING)
  private CashbackStatus status; // PENDING, COMPLETED, CANCELLED

  private LocalDateTime createdAt;
  private LocalDateTime appliedAt; // Когда фактически зачислен на баланс
  private LocalDateTime receivedAt;

  public Long getId() {
    return id;
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

  public Double getPercentage() {
    return percentage;
  }

  public void setPercentage(Double percentage) {
    this.percentage = percentage;
  }

  public CashbackStatus getStatus() {
    return status;
  }

  public void setStatus(CashbackStatus status) {
    this.status = status;
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

  public String getEventId() {
    return eventId;
  }

  public void setEventId(String eventId) {
    this.eventId = eventId;
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) return false;
    Cashback cashback = (Cashback) o;
    return Objects.equals(id, cashback.id);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }
}
