package com.gmail.alexei28.shortcut.kafka.consumer.task5.repo;

import com.gmail.alexei28.shortcut.kafka.consumer.task5.entity.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
  Optional<User> findByEventId(String eventId);

  boolean existsByEventId(String eventId);
}
