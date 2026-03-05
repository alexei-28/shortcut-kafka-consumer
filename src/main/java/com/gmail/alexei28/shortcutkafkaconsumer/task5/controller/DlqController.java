package com.gmail.alexei28.shortcutkafkaconsumer.task5.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gmail.alexei28.shortcutkafkaconsumer.task5.dto.UserDto;
import com.gmail.alexei28.shortcutkafkaconsumer.task5.entity.DlqMessage;
import com.gmail.alexei28.shortcutkafkaconsumer.task5.entity.DlqStatus;
import com.gmail.alexei28.shortcutkafkaconsumer.task5.service.DlqMessageUpdater;
import com.gmail.alexei28.shortcutkafkaconsumer.task5.service.DlqService;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.*;

/*
   REST Controller для retry
   E.g. POST http://localhost:8082/api/v1/dlt/retry/5b2dfa7a-85a0-44c0-b6aa-ef70aef91354

    Endpoint для ручного переотправления:
   - создаем отдельный consumer для task4-topic-dlt - DltConsumer
   - сохраняем сообщения в БД
   - даем endpoint для переотправки в главный топик(task4-topic) - DltController

    Flow:
    DB
     ↓
    deserialize
     ↓
    send to main topic (task4-topic)
     ↓
    markRetried
*/
@RestController
@RequestMapping("/dlt")
public class DlqController {
  private final DlqMessageUpdater dlqMessageUpdater;
  private final DlqService dlqService;
  private final ObjectMapper objectMapper;
  private final KafkaTemplate<String, UserDto> kafkaTemplate;

  private static final Logger logger = LoggerFactory.getLogger(DlqController.class);

  public DlqController(
      DlqMessageUpdater dlqMessageUpdater,
      DlqService dlqService,
      ObjectMapper objectMapper,
      KafkaTemplate<String, UserDto> kafkaTemplate) {
    this.dlqMessageUpdater = dlqMessageUpdater;
    this.dlqService = dlqService;
    this.objectMapper = objectMapper;
    this.kafkaTemplate = kafkaTemplate;
  }

  @GetMapping("/list")
  public ResponseEntity<List<DlqMessage>> list(
      @RequestParam(required = false) String topic,
      @RequestParam(name = "status", required = false) List<DlqStatus> statuses,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          OffsetDateTime from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          OffsetDateTime to) {
    List<DlqMessage> result = dlqService.findByFilter(topic, statuses, from, to);
    return ResponseEntity.ok(result);
  }

  @PostMapping("/retry/{key}")
  public ResponseEntity<String> retry(@PathVariable String key) {
    Optional<DlqMessage> dltMessageOpt = dlqService.getByKey(key);
    if (dltMessageOpt.isEmpty()) {
      logger.warn("retry, No DLT message found for key: {}", key);
      return ResponseEntity.notFound().build();
    }
    DlqMessage dlqMessage = dltMessageOpt.get();
    if (dlqMessage.getStatus() == DlqStatus.IGNORED
        || dlqMessage.getStatus() == DlqStatus.EXHAUSTED) {
      return ResponseEntity.status(HttpStatus.CONFLICT)
          .body(
              "Message with key = "
                  + key
                  + " has status "
                  + dlqMessage.getStatus()
                  + " and cannot be retried");
    }
    dlqService.retry(dlqMessage);
    return ResponseEntity.ok("Retry triggered for messages, key = " + key);
  }

  @PostMapping("/retry")
  public ResponseEntity<String> retryAll(
      @RequestParam(required = false) String topic,
      @RequestParam(required = false) List<DlqStatus> statuses) {
    List<DlqMessage> dlqMessageList = dlqService.findByFilter(topic, statuses, null, null);
    if (dlqMessageList.isEmpty()) {
      return ResponseEntity.notFound().build();
    }
    dlqService.retryAll(dlqMessageList);
    return ResponseEntity.ok("Retry triggered for " + dlqMessageList.size() + " DLT messages");
  }

  // Пометить «разобрано, не нужно» (IGNORED)
  @PostMapping("/ignore/{key}")
  public ResponseEntity<String> ignore(@PathVariable String key) {
    Optional<DlqMessage> dltMessageOpt = dlqService.getByKey(key);
    if (dltMessageOpt.isEmpty()) {
      logger.warn("ignore, No DLT message found for key: {}", key);
      return ResponseEntity.notFound().build();
    }
    DlqMessage dlqMessage = dltMessageOpt.get();
    if (dlqMessage.getStatus() == DlqStatus.IGNORED
        || dlqMessage.getStatus() == DlqStatus.EXHAUSTED) {
      return ResponseEntity.status(HttpStatus.CONFLICT)
          .body(
              "Message with key = "
                  + key
                  + " has status "
                  + dlqMessage.getStatus()
                  + " and cannot be mark as ignored");
    }
    dlqService.markIgnored(key);
    return ResponseEntity.ok("Message marked as ignored: " + key);
  }
}
