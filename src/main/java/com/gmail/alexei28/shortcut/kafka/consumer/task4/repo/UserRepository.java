package com.gmail.alexei28.shortcut.kafka.consumer.task4.repo;

import com.gmail.alexei28.shortcut.kafka.consumer.task4.entity.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
  Optional<User> findByEventId(String eventId);

  boolean existsByEventId(String eventId);
}
