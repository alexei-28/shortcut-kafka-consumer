package com.gmail.alexei28.shortcutkafkaconsumer.task3.repository;

import com.gmail.alexei28.shortcutkafkaconsumer.task3.domain.AccountOperation;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountOperationRepository extends JpaRepository<AccountOperation, UUID> {}
