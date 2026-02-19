package com.gmail.alexei28.shortcutkafkaconsumer;

import com.gmail.alexei28.shortcutkafkaconsumer.configuration.VersionInfoListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class Main {
  private static final Logger logger = LoggerFactory.getLogger(Main.class);

  public static void main(String[] args) {
    SpringApplication app = new SpringApplication(Main.class);
    app.addListeners(new VersionInfoListener());
    app.run(args);

    logger.info("Application started successfully!");
    logger.info(
        "Java version: {}, Java vendor: {}",
        System.getProperty("java.version"),
        System.getProperty("java.vendor"));
  }
}
