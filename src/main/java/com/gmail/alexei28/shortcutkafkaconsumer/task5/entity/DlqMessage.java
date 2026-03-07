package com.gmail.alexei28.shortcutkafkaconsumer.task5.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "task5_dlq_messages")
public class DlqMessage {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  //  idempotency key
  @Column(name = "event_id", nullable = false, unique = true)
  private String eventId;

  @Column(columnDefinition = "TEXT")
  private String payload;

  private String topic;

  private Integer partitionNumber;

  private Long offsetValue;

  @Enumerated(EnumType.STRING)
  private DlqStatus status;

  private Integer retryCount = 0;

  private OffsetDateTime createdAt;

  private OffsetDateTime updatedAt;

  @Column(columnDefinition = "TEXT")
  private String errorMessage;

  @Column private String errorClass;

  @PrePersist
  public void prePersist() {
    createdAt = OffsetDateTime.now();
    updatedAt = OffsetDateTime.now();
    status = DlqStatus.NEW;
  }

  @PreUpdate
  public void preUpdate() {
    updatedAt = OffsetDateTime.now(); // Авто-обновление при любом изменении
  }

  public DlqMessage() {}

  public DlqMessage(
      String eventId, String payload, String topic, Integer partitionNumber, Long offsetValue) {
    this.eventId = eventId;
    this.payload = payload;
    this.topic = topic;
    this.partitionNumber = partitionNumber;
    this.offsetValue = offsetValue;
  }

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getEventId() {
    return eventId;
  }

  public void setEventId(String eventId) {
    this.eventId = eventId;
  }

  public String getPayload() {
    return payload;
  }

  public void setPayload(String payload) {
    this.payload = payload;
  }

  public String getTopic() {
    return topic;
  }

  public void setTopic(String topic) {
    this.topic = topic;
  }

  public Integer getPartitionNumber() {
    return partitionNumber;
  }

  public void setPartitionNumber(Integer partitionNumber) {
    this.partitionNumber = partitionNumber;
  }

  public Long getOffsetValue() {
    return offsetValue;
  }

  public void setOffsetValue(Long offsetValue) {
    this.offsetValue = offsetValue;
  }

  public DlqStatus getStatus() {
    return status;
  }

  public void setStatus(DlqStatus status) {
    this.status = status;
  }

  public Integer getRetryCount() {
    return retryCount;
  }

  public void setRetryCount(Integer retryCount) {
    this.retryCount = retryCount;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public void setErrorMessage(String errorMessage) {
    this.errorMessage = errorMessage;
  }

  public String getErrorClass() {
    return errorClass;
  }

  public void setErrorClass(String errorClass) {
    this.errorClass = errorClass;
  }

  public OffsetDateTime getUpdatedAt() {
    return updatedAt;
  }

  @Override
  public String toString() {
    return "\nDltMessage{"
        + "id="
        + id
        + ", eventId='"
        + eventId
        + '\''
        + ", payload='"
        + payload
        + '\''
        + ", topic='"
        + topic
        + '\''
        + ", partitionNumber="
        + partitionNumber
        + ", offsetValue="
        + offsetValue
        + ", status="
        + status
        + ", retryCount="
        + retryCount
        + ", createdAt="
        + createdAt
        + ", updatedAt="
        + updatedAt
        + ", errorMessage='"
        + errorMessage
        + '\''
        + ", errorClass='"
        + errorClass
        + '\''
        + '}';
  }
}
