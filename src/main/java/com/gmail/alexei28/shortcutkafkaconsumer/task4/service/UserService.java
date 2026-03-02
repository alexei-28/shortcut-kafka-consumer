package com.gmail.alexei28.shortcutkafkaconsumer.task4.service;

import com.gmail.alexei28.shortcutkafkaconsumer.task4.dto.UserDto;
import com.gmail.alexei28.shortcutkafkaconsumer.task4.entity.User;
import com.gmail.alexei28.shortcutkafkaconsumer.task4.interfaces.UserMapper;
import com.gmail.alexei28.shortcutkafkaconsumer.task4.repo.UserRepository;
import jakarta.transaction.Transactional;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/*
  Flow:
   Kafka topic
        ↓
   UserConsumer
        ↓
   UserService
        ↓
   validate
        ↓
       DB
        ↓
   delete from DLT
*/
@Service
public class UserService {
  // Предварительная компиляция паттерна для ускорения работы
  private static final Pattern EMAIL_PATTERN =
      Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
  // ^ - начало, \\d - цифра, {1,20} - от 1 до 20 раз, $ - конец
  private static final Pattern INN_PATTERN = Pattern.compile("^\\d{1,20}$");
  private final UserRepository userRepository;
  private final UserMapper userMapper;
  private final DltService dltService;
  private static final Logger logger = LoggerFactory.getLogger(UserService.class);

  public UserService(UserRepository userRepository, UserMapper userMapper, DltService dltService) {
    this.userRepository = userRepository;
    this.userMapper = userMapper;
    this.dltService = dltService;
  }

  @Transactional
  public void saveUser(UserDto userDto) {
    validate(userDto);
    String eventId = userDto.getEventId().toString();
    // Idempotency защита
    if (userRepository.existsByEventId(eventId)) {
      logger.warn("saveUser, Duplicate eventId detected: {}", eventId);
      return;
    }
    User user = userMapper.toEntity(userDto);
    userRepository.save(user);
    logger.info("saveUser, successfully saved to repo, user: {}", user);
    // Удаление из DLT ТОЛЬКО после успешного сохранения
    dltService.deleteIfExists(eventId);
    logger.info("saveUser, deleted from DLT if existed, eventId: {}", eventId);
  }

  /*
    Если валидация не прошла, то отправляет сообщение в DLQ, благодаря
    KafkaConsumerConfig#errorHandler.addNotRetryableExceptions(IllegalArgumentException.class).
  */
  private void validate(UserDto userDto) {
    String email = userDto.getEmail();
    if (email == null || email.isBlank()) {
      throw new IllegalArgumentException("Email is required");
    }
    if (!EMAIL_PATTERN.matcher(email).matches()) {
      logger.warn("validate, invalid email format: '{}'", email);
      throw new IllegalArgumentException("Invalid email format");
    }

    String inn = userDto.getInn();
    if (inn == null || inn.isBlank()) {
      logger.warn("validate, INN is missing or blank");
      throw new IllegalArgumentException("INN is required");
    }
    if (!INN_PATTERN.matcher(inn).matches()) {
      logger.warn("validate, invalid INN: '{}'. Must be digits only, max length 20", inn);
      throw new IllegalArgumentException("INN must contain 1-20 digits only");
    }

    if (userDto.getAddress() == null || userDto.getAddress().isBlank()) {
      logger.warn("validate, address is missing or blank");
      throw new IllegalArgumentException("Address is required");
    }
  }
}
