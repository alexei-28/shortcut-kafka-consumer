package com.gmail.alexei28.shortcutkafkaconsumer.task4.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "task4_dlt_messages")
public class DltMessage {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(unique = true)
  private String messageKey;

  @Column(columnDefinition = "TEXT")
  private String payload;

  private String topic;

  private Integer partitionNumber;

  private Long offsetValue;

  @Enumerated(EnumType.STRING)
  private DltStatus status;

  private OffsetDateTime createdAt;

  private OffsetDateTime updatedAt;

  @Column(columnDefinition = "TEXT")
  private String errorMessage;

  @Column private String errorClass;

  @PrePersist
  public void prePersist() {
    createdAt = OffsetDateTime.now();
    status = DltStatus.NEW;
  }

  @PreUpdate
  public void preUpdate() {
    updatedAt = OffsetDateTime.now(); // Авто-обновление при любом изменении
  }

  public DltMessage() {}

  public DltMessage(
      String messageKey, String payload, String topic, Integer partitionNumber, Long offsetValue) {
    this.messageKey = messageKey;
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

  public String getMessageKey() {
    return messageKey;
  }

  public void setMessageKey(String messageKey) {
    this.messageKey = messageKey;
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

  public DltStatus getStatus() {
    return status;
  }

  public void setStatus(DltStatus status) {
    this.status = status;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(OffsetDateTime createdAt) {
    this.createdAt = createdAt;
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

  public void setUpdatedAt(OffsetDateTime updatedAt) {
    this.updatedAt = updatedAt;
  }

  @Override
  public String toString() {
    return "DltMessage{"
        + "id="
        + id
        + ", messageKey='"
        + messageKey
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
