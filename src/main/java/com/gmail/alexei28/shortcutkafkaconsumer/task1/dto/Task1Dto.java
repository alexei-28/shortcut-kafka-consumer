package com.gmail.alexei28.shortcutkafkaconsumer.task1.dto;

import java.time.LocalDateTime;
import java.util.Objects;

public class Task1Dto {
  private Long number;
  private String content;
  private LocalDateTime receivedAt;

  public Task1Dto() {}

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

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) return false;
    Task1Dto task1Dto = (Task1Dto) o;
    return Objects.equals(number, task1Dto.number)
        && Objects.equals(content, task1Dto.content)
        && Objects.equals(receivedAt, task1Dto.receivedAt);
  }

  @Override
  public int hashCode() {
    return Objects.hash(number, content, receivedAt);
  }

  @Override
  public String toString() {
    return "Task1Dto{"
        + "number = "
        + number
        + ", content = '"
        + content
        + '\''
        + ", receivedAt = "
        + receivedAt
        + '}';
  }
}
