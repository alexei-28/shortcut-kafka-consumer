package com.gmail.alexei28.shortcutkafkaconsumer.task3.repository;

import com.gmail.alexei28.shortcutkafkaconsumer.task3.domain.Transfer;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransferRepository extends JpaRepository<Transfer, UUID> {

  Optional<Transfer> findByOperationId(UUID operationId);
}
