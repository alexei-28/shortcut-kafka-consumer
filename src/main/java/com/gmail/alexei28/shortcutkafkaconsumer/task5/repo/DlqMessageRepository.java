package com.gmail.alexei28.shortcutkafkaconsumer.task5.repo;

import com.gmail.alexei28.shortcutkafkaconsumer.task5.entity.DlqMessage;
import com.gmail.alexei28.shortcutkafkaconsumer.task5.entity.DlqStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DlqMessageRepository extends JpaRepository<DlqMessage, Long> {

  Optional<DlqMessage> findByEventId(String eventId);

  // Find all messages by their current status (e.g., NEW, FAILED, PROCESSED)
  List<DlqMessage> findByStatus(DlqStatus status);

  // Find all messages originating from a specific Kafka/MQ topic
  List<DlqMessage> findByTopic(String topic);

  // Bonus: Often useful for DLQ processing
  List<DlqMessage> findByStatusAndTopic(DlqStatus status, String topic);
}
