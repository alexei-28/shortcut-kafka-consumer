package com.gmail.alexei28.shortcutkafkaconsumer.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.LocalDateTime;
import java.util.Objects;

/*
   Чтобы Spring смог «собрать» объект из JSON, ваш класс Message должен соответствовать двум правилам:
   - Конструктор по умолчанию: Jackson сначала создает «пустой» объект через конструктор без аргументов, а затем заполняет его данными.
   - Геттеры и Сеттеры: Либо поля должны быть публичными (что плохо), либо у вас должны быть стандартные
     get/set методы (или аннотации Lombok @Data/@Getter+@Setter).
*/
@Entity
public class Message {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @JsonIgnore
  private Long id;

  private Long number;
  private String content;
  private LocalDateTime receivedAt;

  public Message() {
    // JPA requires a no-args constructor
  }

  public Message(long number, String content, LocalDateTime receivedAt) {
    this.number = number;
    this.content = content;
    this.receivedAt = receivedAt;
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
    Message message = (Message) o;
    return Objects.equals(number, message.number);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(number);
  }

  @Override
  public String toString() {
    return "Message{"
        + "id ="
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
