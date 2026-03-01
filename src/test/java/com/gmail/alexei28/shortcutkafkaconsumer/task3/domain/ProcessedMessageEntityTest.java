package com.gmail.alexei28.shortcutkafkaconsumer.task3.domain;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;
import org.junit.jupiter.api.Test;

class ProcessedMessageEntityTest {
  @Test
  void simpleEqualsContract() {
    EqualsVerifier.forClass(ProcessedMessageEntity.class)
        .suppress(Warning.SURROGATE_KEY)
        .suppress(Warning.IDENTICAL_COPY_FOR_VERSIONED_ENTITY)
        .verify();
  }
}
