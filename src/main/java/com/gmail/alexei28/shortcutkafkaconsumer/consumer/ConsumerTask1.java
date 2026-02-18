package com.gmail.alexei28.shortcutkafkaconsumer.consumer;

import com.gmail.alexei28.shortcutkafkaconsumer.dto.Task1Dto;
import com.gmail.alexei28.shortcutkafkaconsumer.entity.Task1;
import com.gmail.alexei28.shortcutkafkaconsumer.intrefaces.TaskMapper;
import com.gmail.alexei28.shortcutkafkaconsumer.repo.Task1Repository;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/*
   Задача 1 — "Аналитика экранов мобильного приложения"
   Продуктовая команда собирает данные о том, какие экраны клиент открывает в мобильном приложении.
   Данные идут в ML-пайплайн для рекомендаций.
   Что говорит бизнес:
    - Нагрузка ~50 000 событий/сек
    - "Потерять пару процентов — ок, модель переживёт. Но дубли портят модель — экраны кажутся популярнее, чем есть"
    - Нужна максимальная скорость, инфраструктуру раздувать нельзя
   Задание: реализуй producer и consumer на Spring Kafka. Выбери подходящую гарантию доставки и обоснуй конфигурацию.
   Решение:
   Гарантии доставки сообщений: "at-most-once" (Не более одного раза).
   Брокер попытается один раз доставить сообщение потребителю, но если доставка сорвалась, повторных попыток не будет,
   т.е. НИКОГДА не будет дубликата.
   Такую гарантию выбирают, когда пропуск редких сообщений не критичен.
   Главное правило: offset должен быть закоммичен ДО обработки сообщения:
     pull -> commit offset -> process

   - Как получить "at-most-once" в Spring Kafka:
     Для строгой реализации "сначала подтверди, потом делай" лучше использовать ручное управление смещениями.
     1. Настройка AckMode:
        Нужно установить режим MANUAL_IMMEDIATE, чтобы коммит уходил в Kafka мгновенно при вызове метода.
          spring.kafka.listener.ack-mode = MANUAL_IMMEDIATE
     2. Реализация в коде:
        В методе слушателя вызываем ack.acknowledge() первой же строкой, до начала любой полезной работы.

   - Когда это оправдано?
     -High-throughput системы: Где пропускная способность важнее точности
      (например, поток координат курьера в реальном времени — если одна точка потеряется, следующая через секунду это исправит).
     -Неидемпотентные операции: Когда повторная обработка вызовет критическую ошибку, а отсутствие данных — лишь небольшое неудобство.
    Важно: Никогда не используйте этот режим для финансовых транзакций!

    - Что нужно проверить в тестах?
    Нужно проверить конкретное свойство:
      Если consumer падает во время обработки, сообщение не будет перечитано повторно.
    То есть:
        -offset уже закоммичен
        -запись в БД отсутствует
        -повторный запуск consumer НЕ приводит к повторному получению сообщения
    Это и есть единственное корректное доказательство at-most-once.

    Главная идея теста:
    Мы специально заставим consumer упасть после ack.acknowledge().
    Если стратегия at-most-once действительно работает:
    Событие	-> Результат
    Сообщение -> offset committed
    Обработка упала -> БД пустая
    Контейнер KafkaListener перезапущен -> сообщение НЕ придёт снова

    Если сообщение придёт снова → это at-least-once, а не at-most-once.

    Steps:
    1.Producer: Task1Entity (id=1, number=101) -> MapStruct -> Task1Dto (number=101).
    2.Kafka: Передает JSON { "number": 101, ... }.
    3.Consumer: Получает JSON -> Jackson -> Task1Dto.
    4.Consumer: Task1Dto -> MapStruct -> Task1 (id=null, number=101).
    5.DB: Сохраняет Task1, генерируя новый id (например, 50).
*/
@Service
public class ConsumerTask1 {
  private final Task1Repository repository;
  private final TaskMapper taskMapper;
  private static final Logger logger = LoggerFactory.getLogger(ConsumerTask1.class);

  public ConsumerTask1(TaskMapper taskMapper, Task1Repository repository) {
    this.taskMapper = taskMapper;
    this.repository = repository;
  }

  /*
  - ack.acknowledge() в начале: Подтверждаем получение сообщения до завершения бизнес-логики.
  - Блок try-catch: В методе consume исключение перехватывается и просто логируется.
    Это гарантирует, что контейнер Kafka не будет пытаться переповторить (retry) доставку,
    так как для него метод завершился "успешно" (без выброса исключения наружу).

    Spring Kafka при десериализации ищет заголовок __TypeId__
  */
  @Transactional
  @KafkaListener(topics = "${app.kafka.topics.task1}", groupId = "${app.kafka.groups.task1}")
  public void consume(Task1Dto task1Dto, Acknowledgment ack) {
    // Сначала фиксируем смещение(commit offset) в Kafka согласно стратегии at-most-once
    ack.acknowledge();
    try {
      logger.info("consume, received from Kafka, task1Dto: {}", task1Dto);
      // Конвертируем DTO обратно в Entity
      Task1 task1 = taskMapper.toEntity(task1Dto);
      // Теперь выполняем логику. Если здесь будет Exception, сообщение уже "считается" прочитанным.
      // Eсли доставка сорвалась, повторных попыток не будет, т.е. НИКОГДА не будет дубликата.
      process(task1);
    } catch (Exception e) {
      logger.error("consume, Error processing, task1 lost according to at-most-once strategy", e);
    }
  }

  private void process(Task1 task1) {
    if (task1.getReceivedAt() == null) {
      task1.setReceivedAt(LocalDateTime.now());
    }
    repository.save(task1);
    logger.info("process, successfully saved to database, task1: {}", task1);
  }
}
