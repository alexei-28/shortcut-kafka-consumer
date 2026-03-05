package com.gmail.alexei28.shortcutkafkaconsumer.task4.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gmail.alexei28.shortcutkafkaconsumer.task4.dto.UserDto;
import com.gmail.alexei28.shortcutkafkaconsumer.task4.entity.DltMessage;
import com.gmail.alexei28.shortcutkafkaconsumer.task4.service.DltService;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
public class DltController {
  private final DltService dltService;
  private final ObjectMapper objectMapper;
  private final KafkaTemplate<String, UserDto> kafkaTemplate;

  @Value("${app.kafka.topics.task4}")
  private String mainTopic;

  private static final Logger logger = LoggerFactory.getLogger(DltController.class);

  public DltController(
      DltService dltService,
      ObjectMapper objectMapper,
      KafkaTemplate<String, UserDto> kafkaTemplate) {
    this.dltService = dltService;
    this.objectMapper = objectMapper;
    this.kafkaTemplate = kafkaTemplate;
  }

  @PostMapping("/retry/{key}")
  public ResponseEntity<String> retry(@PathVariable String key) {
    logger.info("retry, Received retry request for key: {}", key);
    Optional<DltMessage> messageOpt = dltService.getByKey(key);
    if (messageOpt.isEmpty()) {
      logger.warn("retry, No DLT message found for key: {}", key);
      return ResponseEntity.notFound().build();
    }
    DltMessage dltMessage = messageOpt.get();
    try {
      UserDto userDto = objectMapper.readValue(dltMessage.getPayload(), UserDto.class);
      kafkaTemplate.send(mainTopic, key, userDto);
      dltService.markRetried(key);
      logger.info(
          "retry, Successfully re-sent message with key: {} to main topic: {}", key, mainTopic);
      return ResponseEntity.ok("Message re-sent to main topic");
    } catch (Exception ex) {
      // e.g invalid json
      logger.error(
          "retry, Failed to re-send message with key: {} to main topic: {}, error: {}",
          key,
          mainTopic,
          ex.getMessage());
      dltService.markFailed(key);
      return ResponseEntity.internalServerError().body("Retry failed");
    }
  }
}
