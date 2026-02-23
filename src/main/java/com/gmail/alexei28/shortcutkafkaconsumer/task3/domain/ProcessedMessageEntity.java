package com.gmail.alexei28.shortcutkafkaconsumer.task3.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "processed_messages")
public class ProcessedMessageEntity {
  // Только unique constraint гарантирует атомарность.
  @Id // Используем eventId как первичный ключ
  @Column(name = "event_id", nullable = false, updatable = false)
  private UUID eventId;

  @Column(name = "processed_at", nullable = false, updatable = false)
  private OffsetDateTime processedAt;

  public ProcessedMessageEntity() {}

  public ProcessedMessageEntity(UUID eventId) {
    this.eventId = eventId;
  }

  public UUID getEventId() {
    return eventId;
  }

  public void setEventId(UUID eventId) {
    this.eventId = eventId;
  }

  public OffsetDateTime getProcessedAt() {
    return processedAt;
  }

  public void setProcessedAt(OffsetDateTime processedAt) {
    this.processedAt = processedAt;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ProcessedMessageEntity that)) return false;
    return Objects.equals(eventId, that.eventId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(eventId);
  }

  @Override
  public String toString() {
    return "\nProcessedMessageEntity{"
        + ",\n eventId = "
        + eventId
        + ",\n processedAt = "
        + processedAt
        + "\n"
        + '}';
  }
}
