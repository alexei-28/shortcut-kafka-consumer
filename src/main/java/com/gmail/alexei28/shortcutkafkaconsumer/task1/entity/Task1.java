package com.gmail.alexei28.shortcutkafkaconsumer.task1.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
public class Task1 {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private Long number;
  private String content;
  private LocalDateTime receivedAt;

  public Task1() {
    // JPA requires a no-args constructor
  }

  // --- Getters and Setters ---

  public Long getId() {
    return id;
  }

  public Long getNumber() {
    return number;
  }

  public void setNumber(Long number) {
    this.number = number;
  }

  public String getContent() {
    return content;
  }

  public void setContent(String content) {
    this.content = content;
  }

  public LocalDateTime getReceivedAt() {
    return receivedAt;
  }

  public void setReceivedAt(LocalDateTime receivedAt) {
    this.receivedAt = receivedAt;
  }

  // --- equals and hashCode (IMPORTANT for JPA entities) ---

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) return false;
    Task1 task1 = (Task1) o;
    return Objects.equals(id, task1.id);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }

  @Override
  public String toString() {
    return "Task1 {"
        + "id = "
        + id
        + ", number = "
        + number
        + ", content = '"
        + content
        + '\''
        + ", receivedAt = "
        + receivedAt
        + '}';
  }
}
