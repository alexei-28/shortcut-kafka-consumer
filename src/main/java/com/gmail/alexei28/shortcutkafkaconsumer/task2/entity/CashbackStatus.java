package com.gmail.alexei28.shortcutkafkaconsumer.task2.entity;

public enum CashbackStatus {
  PENDING, // Ожидает подтверждения (например, 14 дней на возврат товара)
  COMPLETED, // Успешно начислен
  CANCELLED // Отменен (возврат товара или ошибка)
}
