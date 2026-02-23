package com.gmail.alexei28.shortcutkafkaconsumer.task3.idempotency;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
    name = "processed_messages",
    uniqueConstraints = {
      @UniqueConstraint(name = "uk_processed_event_id", columnNames = "event_id")
    })
public class ProcessedMessageEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  // ID outbox события (НЕ Kafka offset!)
  @Column(name = "event_id", nullable = false, updatable = false)
  private UUID eventId;

  @Column(name = "processed_at", nullable = false, updatable = false)
  private OffsetDateTime processedAt;

  @PrePersist
  void prePersist() {
    processedAt = OffsetDateTime.now();
  }

  public void setId(UUID id) {
    this.id = id;
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
  public String toString() {
    return "\nProcessedMessageEntity{"
        + "\nid="
        + id
        + ",\n eventId="
        + eventId
        + ",\n processedAt="
        + processedAt
        + "\n"
        + '}';
  }
}
