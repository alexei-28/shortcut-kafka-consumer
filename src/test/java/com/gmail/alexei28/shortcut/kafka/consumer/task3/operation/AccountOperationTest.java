package com.gmail.alexei28.shortcut.kafka.consumer.task3.operation;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;
import org.junit.jupiter.api.Test;

class AccountOperationTest {
  @Test
  void simpleEqualsContract() {
    EqualsVerifier.forClass(AccountOperation.class)
        .withOnlyTheseFields("externalId")
        .suppress(Warning.IDENTICAL_COPY_FOR_VERSIONED_ENTITY)
        .verify();
  }
}
