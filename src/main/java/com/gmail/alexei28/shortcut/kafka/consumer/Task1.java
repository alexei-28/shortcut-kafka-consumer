package com.gmail.alexei28.shortcut.kafka.consumer;

import com.gmail.alexei28.shortcut.kafka.consumer.configuration.VersionInfoListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/*
    Check health http://localhost:8081/api/v1/actuator/health
*/
@SpringBootApplication
public class Task1 {
  private static final Logger logger = LoggerFactory.getLogger(Task1.class);

  public static void main(String[] args) {
    SpringApplication app = new SpringApplication(Task1.class);
    app.addListeners(new VersionInfoListener());
    app.run(args);
    logger.info(
        "\n\n ===== Application started successfully! =====\n\tЗадача 1 - Аналитика экранов мобильного приложения");
    logger.info(
        "Java version: {}, Java vendor: {}",
        System.getProperty("java.version"),
        System.getProperty("java.vendor"));
  }
}
