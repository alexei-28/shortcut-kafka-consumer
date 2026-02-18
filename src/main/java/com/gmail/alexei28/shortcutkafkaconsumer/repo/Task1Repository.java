package com.gmail.alexei28.shortcutkafkaconsumer.repo;

import com.gmail.alexei28.shortcutkafkaconsumer.entity.Task1;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface Task1Repository extends JpaRepository<Task1, Long> {
  Optional<Task1> findByNumber(Long number);
}
