package com.gmail.alexei28.shortcutkafkaconsumer;

import com.gmail.alexei28.shortcutkafkaconsumer.configuration.VersionInfoListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class Task4 {
  private static final Logger logger = LoggerFactory.getLogger(Task4.class);

  public static void main(String[] args) {
    SpringApplication app = new SpringApplication(Task4.class);
    app.addListeners(new VersionInfoListener());
    app.run(args);

    logger.info(
        "\n\n ===== Application started successfully! =====\nЗадача 4 - Битые сообщения из CFT");
    logger.info(
        "Java version: {}, Java vendor: {}",
        System.getProperty("java.version"),
        System.getProperty("java.vendor"));
  }
}
