package com.gmail.alexei28.shortcut.kafka.consumer;

import com.gmail.alexei28.shortcut.kafka.consumer.configuration.VersionInfoListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class Task3 {
  private static final Logger logger = LoggerFactory.getLogger(Task3.class);

  public static void main(String[] args) {
    SpringApplication app = new SpringApplication(Task3.class);
    app.addListeners(new VersionInfoListener());
    app.run(args);
    logger.info("\n\n===== Application started successfully!=====\nЗадача 3 — Перевод через СБП");
    logger.info(
        "Java version: {}, Java vendor: {}",
        System.getProperty("java.version"),
        System.getProperty("java.vendor"));
  }
}
