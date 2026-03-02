package com.gmail.alexei28.shortcutkafkaconsumer.task4.repo;

import com.gmail.alexei28.shortcutkafkaconsumer.task4.entity.DltMessage;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DltMessageRepository extends JpaRepository<DltMessage, Long> {

  Optional<DltMessage> findByMessageKey(String messageKey);
}
