package com.gmail.alexei28.shortcutkafkaconsumer.repo;

import com.gmail.alexei28.shortcutkafkaconsumer.entity.Message;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {
  Optional<Message> findByNumber(Long number);

  // Spring Data JPA can derive this automatically
  void deleteByNumber(Long number);
}
