package com.gmail.alexei28.shortcutkafkaconsumer.task3.repo;

import com.gmail.alexei28.shortcutkafkaconsumer.task3.domain.ProcessedMessageEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProcessedMessageRepository extends JpaRepository<ProcessedMessageEntity, UUID> {
  @Modifying
  @Query(
      value =
          """
        INSERT INTO processed_messages (event_id, processed_at)
        VALUES (:eventId, now())
        ON CONFLICT (event_id) DO NOTHING
        """,
      nativeQuery = true)
  int insert(@Param("eventId") UUID eventId);
}
