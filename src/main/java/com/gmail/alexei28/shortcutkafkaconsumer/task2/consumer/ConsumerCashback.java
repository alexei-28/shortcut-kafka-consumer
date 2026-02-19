package com.gmail.alexei28.shortcutkafkaconsumer.task2.consumer;

import com.gmail.alexei28.shortcutkafkaconsumer.task2.dto.CashbackDto;
import com.gmail.alexei28.shortcutkafkaconsumer.task2.entity.Cashback;
import com.gmail.alexei28.shortcutkafkaconsumer.task2.interfaces.CashbackMapper;
import com.gmail.alexei28.shortcutkafkaconsumer.task2.repo.CashbackRepository;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/*
    Задача 2 - "Начисление кэшбэка"
    После покупки по карте клиенту начисляется кэшбэк. Событие приходит из процессингового центра.
    Что говорит бизнес:
    Клиент не получил кэшбэк — жалоба в ЦБ. Терять события нельзя.
    Двойное начисление — неприятно, но ежедневная сверка поймает. Допустимо как редкий случай.
    Кэшбэк считается начисленным только после записи в PostgreSQL.
    Задание: реализуй producer и consumer.
    Обоснуй гарантию. В коде комментарием покажи точку, где возможен побочный эффект выбранного подхода.

    Решение:
    Гарантии доставки сообщений: "at-least-once" (минимум один раз).
    Брокер будет пытаться доставить сообщение до тех пор, пока не получит подтверждение об успешной обработке.
    Если в сети был сбой, брокер предпримет повторную доставку. В результате одно и то же сообщение может прийти дважды,
    поэтому в коде потребителя нужно заложить обработку дубликатов (идемпотентность).
    Для реализации гарантии at-least-once в Spring Kafka, основной принцип заключается в том,
    что мы подтверждаем получение сообщения (commit offset) только после того, как успешно выполнили всю бизнес-логику
    и сохранили данные в базу.
    Если на любом этапе (маппинг, сохранение в БД, внешняя интеграция) произойдет ошибка, ack.acknowledge() не будет вызван,
    и при следующем перезапуске или по истечении таймаута Kafka отправит это сообщение повторно.

    Правильная схема at-least-once:
     1.получили сообщение
     2.обработали
     3.сохранили в БД
     4.ТОЛЬКО ПОТОМ commit offset

    Почему это гарантирует At-least-once?
   -Защита от потерь: Если база данных (PostgreSQL) будет недоступна, транзакция откатится, ack не отправится. После исправления БД сообщение будет прочитано снова.
   -Побочный эффект — Дубликаты: Это обратная сторона медали. Если repository.save() выполнится успешно, но в этот же миг упадет сеть или само приложение до того, как ack.acknowledge() дойдет до Kafka, то после перезапуска сообщение придет второй раз.
   -Решение дубликатов: Для полной надежности на стороне БД стоит использовать Idempotency.
    Поле order_id помечено как unique = true — это и есть защита.
    Второй раз такой же заказ просто не вставится (возникнет UniqueConstraintViolation), что предотвратит двойное начисление денег клиенту.

    Теперь поведение:
    consumer умер -> сообщение придёт снова
    DB exception  -> сообщение придёт снова
    timeout       -> сообщение придёт снова
    ребаланс      -> сообщение придёт снова

    Это и есть at-least-once delivery.

    Но теперь появляется новая проблема:
     Kafka теперь может прислать одно и то же сообщение 2-5 раз.
     Пример:
     1. consumer получил сообщение
     2. сохранил в БД
     3. offset ещё не закоммичен
     4. consumer упал (SIGKILL / OOM / docker restart)
     5. Kafka думает: "не обработано"
     6. присылает сообщение снова

     Но БД уже содержит запись. Вот поэтому at-least-once всегда = повторы.

     Поэтому at-least-once ВСЕГДА требует идемпотентность (идемпотентный consumer).
     Что произойдёт теперь при падении:
     Где упал сервис:
        -до save -> Kafka пришлёт снова
        -после save, до ack -> Kafka пришлёт снова(но БД защитит)
        -после ack -> Kafka не пришлёт

     Идемпотентность защищает именно обработку события.
     Важно:
     Idempotency key должен идентифицировать не объект, а сообщение.
     -orderId защищает от повторного создания заказа.
     -eventId защищает от повторной доставки Kafka.
     И для at-least-once тебе нужен именно eventId.

     Итог:
     Чтобы действительно была гарантия at-least-once:
     Обязательно:
     1.enable-auto-commit=false
     2.ack-mode=manual
     3.ack.acknowledge() после repository.save
     4.@Transactional
     5.eventId unique
     6.existsByEventId проверка

     Важный вывод
     eventId — это и есть idempotency key.
     В event-driven системах правило:
     Каждое событие должно быть обработано не более одного раза.
     И единственный надёжный способ это обеспечить —
     уникальный ключ события в персистентном хранилище.
     Итог:
      -eventId обязательно хранить в Cashback
      -eventId обязательно UNIQUE
      -идемпотентность обеспечивает БД, а не сервис
      -это стандартный паттерн Kafka idempotent consumer

    И тогда поведение системы станет:
     Kafka может прислать сообщение сколько угодно раз,но пользователь получит кэшбэк ровно один раз.
     Cообщение не потеряется и не будет двойного начисления.
     0 или более повторов -> но ровно 1 запись в БД

    Spring не завершит метод успешно, пока транзакция БД реально не закоммитится.
    @Transactional -> Kafka доставила сообщение -> PostgreSQL exception -> Kafka offset НЕ будет закоммичен -> Kafka пришлёт снова.
    Важно:
     ack.acknowledge() не сразу коммитит offset.
     Он только говорит: "можно зафиксировать после успешного завершения listener".
     А успешное завершение listener в Spring = метод завершился без exception И транзакция закоммичена.
     Поэтому @Transactional критичен.

    Когда @Transactional НЕ нужен
    Только если consumer:
     -ничего не пишет в БД
     -просто логирует
     -или вызывает идемпотентный внешний API
    Во всех остальных случаях — нужен.
*/
@Service
public class ConsumerCashback {
  private final CashbackRepository repository;
  private final CashbackMapper cashbackMapper;
  private static final Logger logger = LoggerFactory.getLogger(ConsumerCashback.class);

  public ConsumerCashback(CashbackMapper cashbackMapper, CashbackRepository repository) {
    this.cashbackMapper = cashbackMapper;
    this.repository = repository;
  }

  /*
   Steps:
   1. Получили Kafka message
   2. Проверили есть ли eventId в БД (База гарантирует атомарность)
   3. Если есть -> ACK и игнор
   4. Если нет -> сохранить + ACK
  */
  @Transactional
  @KafkaListener(topics = "${app.kafka.topics.task2}", groupId = "${app.kafka.groups.task2}")
  public void consume(CashbackDto cashbackDto, Acknowledgment ack) {
    try {
      logger.info("consume, processing cashbackDto: {}", cashbackDto);
      // 1. Конвертируем DTO в Entity
      Cashback cashback = cashbackMapper.toEntity(cashbackDto);
      // 2. Выполняем бизнес-логику и сохраняем в БД
      process(cashback);
      // 3. ТОЧКА ГАРАНТИИ: commit offset ТОЛЬКО после успеха в БД.
      // Запись в БД прошла -> значит событие новое
      ack.acknowledge();
      logger.info("consume, successfully processed and acknowledged: {}", cashback.getOrderId());
    } catch (DataIntegrityViolationException e) {
      // Ловим ошибку уникальности
      logger.warn(
          "consume, duplicate message detected for eventId: {}. Skipping.",
          cashbackDto.getEventId());
      // Всё равно commit offset, чтобы Kafka не слала этот дубликат вечно
      ack.acknowledge();
    }
  }

  private void process(Cashback cashback) {
    if (cashback.getReceivedAt() == null) {
      cashback.setReceivedAt(LocalDateTime.now());
    }
    repository.save(cashback);
    logger.info("process, successfully saved to database, task2: {}", cashback);
  }
}
