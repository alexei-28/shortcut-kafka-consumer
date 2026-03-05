package com.gmail.alexei28.shortcutkafkaconsumer.task5.entity;

public enum DlqStatus {
  NEW, // Еще не пробовали
  RETRIED, // В процессе переповтора
  FAILED, // Ошибка (временная или постоянная)
  EXHAUSTED, // Попытки исчерпаны, больше не трогаем
  IGNORED;
}
