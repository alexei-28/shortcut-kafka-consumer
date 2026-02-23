package com.gmail.alexei28.shortcutkafkaconsumer.task3.repository;

import com.gmail.alexei28.shortcutkafkaconsumer.task3.idempotency.ProcessedMessageEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedMessageRepository extends JpaRepository<ProcessedMessageEntity, UUID> {

  boolean existsByEventId(UUID eventId);
}
