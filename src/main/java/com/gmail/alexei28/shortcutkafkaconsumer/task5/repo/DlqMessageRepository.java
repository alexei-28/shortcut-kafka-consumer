package com.gmail.alexei28.shortcutkafkaconsumer.task5.repo;

import com.gmail.alexei28.shortcutkafkaconsumer.task5.entity.DlqMessage;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DlqMessageRepository extends JpaRepository<DlqMessage, Long> {

  Optional<DlqMessage> findByEventId(String eventId);
}
