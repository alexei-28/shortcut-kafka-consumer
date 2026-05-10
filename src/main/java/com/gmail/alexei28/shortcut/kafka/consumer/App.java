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
public class App {
  private static final Logger logger = LoggerFactory.getLogger(App.class);

  public static void main(String[] args) {
    SpringApplication app = new SpringApplication(App.class);
    app.addListeners(new VersionInfoListener());
    app.run(args);
    logger.info("Application started successfully!");
    logger.info(
        "Java version: {}, Java vendor: {}",
        System.getProperty("java.version"),
        System.getProperty("java.vendor"));
  }
}
