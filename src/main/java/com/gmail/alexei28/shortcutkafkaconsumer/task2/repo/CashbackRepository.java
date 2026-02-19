package com.gmail.alexei28.shortcutkafkaconsumer.task2.repo;

import com.gmail.alexei28.shortcutkafkaconsumer.task2.entity.Cashback;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CashbackRepository extends JpaRepository<Cashback, Long> {
  Optional<Cashback> findByEventId(String eventId);
}
