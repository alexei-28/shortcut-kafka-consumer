package com.gmail.alexei28.shortcut.kafka.consumer.task3.repo;

import com.gmail.alexei28.shortcut.kafka.consumer.task3.operation.AccountOperation;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountOperationRepository extends JpaRepository<AccountOperation, UUID> {}
