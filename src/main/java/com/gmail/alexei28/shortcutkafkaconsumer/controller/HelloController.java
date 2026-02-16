package com.gmail.alexei28.shortcutkafkaconsumer.controller;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping
public class HelloController {
  private static final Logger logger = LoggerFactory.getLogger(HelloController.class);

  // e.g. http://localhost:8082/api/v1/hello
  @GetMapping("/hello")
  public String sayHello(@RequestParam(value = "name", defaultValue = "Consumer") String name) {
    logger.info("sayHello, name: {}", name);
    String content =
        String.format(
            "Hello, %s!%nCurrent date: %s",
            name, LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
    return wrapInHtml(content);
  }

  public String wrapInHtml(String content) {
    return """
           <html>
             <body>
               <pre>%s</pre>
             </body>
           </html>
           """
        .formatted(content);
  }
}
