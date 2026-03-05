package com.gmail.alexei28.shortcutkafkaconsumer.task5.service;

import com.gmail.alexei28.shortcutkafkaconsumer.task5.entity.DlqMessage;
import com.gmail.alexei28.shortcutkafkaconsumer.task5.entity.DlqStatus;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class DlqRetrySchedulerService {

  private final DlqService dlqService;
  private static final Logger logger = LoggerFactory.getLogger(DlqRetrySchedulerService.class);

  public DlqRetrySchedulerService(DlqService dlqService) {
    this.dlqService = dlqService;
  }

  @Scheduled(fixedRateString = "${app.kafka.retry.rate-ms:60000}")
  public void retryMessages() {
    // Выбираем только те статусы, которые имеют смысл для повтора
    List<DlqStatus> statuses = List.of(DlqStatus.NEW, DlqStatus.FAILED, DlqStatus.RETRIED);
    // Получаем кандидатов и фильтруем по количеству попыток
    List<DlqMessage> dlqMessageFilteredList =
        dlqService.findByFilter(null, statuses, null, null).stream().toList();
    if (!dlqMessageFilteredList.isEmpty()) {
      logger.info("retryMessages, attempting to retry {} messages", dlqMessageFilteredList.size());
      dlqService.retryAll(dlqMessageFilteredList);
    }
  }
}
