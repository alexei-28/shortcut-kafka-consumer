package com.gmail.alexei28.shortcutkafkaconsumer.task3.domain;

public enum TransferStatus {
  NEW, // создан
  VALIDATED, // проверен
  SENT_TO_CLEARING, // отправлен в клиринг
  COMPLETED, // успешно проведен
  REJECTED, // отклонен
  FAILED // ошибка обработки
}
