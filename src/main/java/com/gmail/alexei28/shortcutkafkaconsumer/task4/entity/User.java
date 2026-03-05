package com.gmail.alexei28.shortcutkafkaconsumer.task4.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
    name = "task4_users",
    uniqueConstraints = {
      @UniqueConstraint(name = "uk_user_event_id", columnNames = "event_id"),
      @UniqueConstraint(name = "uk_user_user_id", columnNames = "user_id")
    },
    indexes = {
      @Index(name = "idx_user_event_id", columnList = "event_id"),
      @Index(name = "idx_user_user_id", columnList = "user_id"),
      @Index(name = "idx_user_status", columnList = "status")
    })
public class User {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  //  idempotency key
  @Column(name = "event_id", nullable = false, updatable = false)
  private String eventId;

  @Column(name = "user_id", nullable = false, unique = true, updatable = false)
  private String userId;

  @Enumerated(EnumType.STRING)
  private UserStatus status;

  @Column(name = "first_name", nullable = false, length = 100)
  private String firstName;

  @Column(name = "last_name", nullable = false, length = 100)
  private String lastName;

  @Column(name = "email", length = 150)
  private String email;

  @Column(name = "inn", length = 20)
  private String inn; // ИНН

  @Column(name = "address", nullable = false)
  private String address;

  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @Column(nullable = false)
  private OffsetDateTime updatedAt;

  // createdAt → устанавливается один раз при создании (@PrePersist)
  @PrePersist
  public void prePersist() {
    createdAt = OffsetDateTime.now();
    status = UserStatus.NEW;
    updatedAt = createdAt;
  }

  // автоматически вызывается перед обновлением сущности в базе данных.
  // updatedAt → каждый раз при обновлении (@PreUpdate)
  @PreUpdate
  public void preUpdate() {
    updatedAt = OffsetDateTime.now();
  }

  public User() {}

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public String getEventId() {
    return eventId;
  }

  public void setEventId(String eventId) {
    this.eventId = eventId;
  }

  public String getFirstName() {
    return firstName;
  }

  public void setFirstName(String firstName) {
    this.firstName = firstName;
  }

  public String getLastName() {
    return lastName;
  }

  public void setLastName(String lastName) {
    this.lastName = lastName;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public String getInn() {
    return inn;
  }

  public void setInn(String inn) {
    this.inn = inn;
  }

  public String getAddress() {
    return address;
  }

  public void setAddress(String address) {
    this.address = address;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(OffsetDateTime createdAt) {
    this.createdAt = createdAt;
  }

  public OffsetDateTime getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(OffsetDateTime updatedAt) {
    this.updatedAt = updatedAt;
  }

  public UserStatus getStatus() {
    return status;
  }

  public void setStatus(UserStatus status) {
    this.status = status;
  }

  public String getUserId() {
    return userId;
  }

  public void setUserId(String userId) {
    this.userId = userId;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof User other)) return false;
    return getId() != null && getId().equals(other.getId());
  }

  @Override
  public int hashCode() {
    return getClass().hashCode();
  }

  @Override
  public String toString() {
    return "\nUser{"
        + "\nid="
        + id
        + ",\n eventId='"
        + eventId
        + ",\n userId='"
        + userId
        + '\''
        + ",\n status="
        + status
        + ",\n firstName='"
        + firstName
        + '\''
        + ",\n lastName='"
        + lastName
        + '\''
        + ",\n email='"
        + email
        + '\''
        + ",\n inn='"
        + inn
        + '\''
        + ",\n address='"
        + address
        + '\''
        + ",\n createdAt="
        + createdAt
        + ",\n updatedAt="
        + updatedAt
        + '}';
  }
}
