package com.gmail.alexei28.shortcutkafkaconsumer.task4.entity;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;
import org.junit.jupiter.api.Test;

class UserTest {
  @Test
  void simpleEqualsContract() {
    EqualsVerifier.forClass(User.class)
        .suppress(Warning.SURROGATE_KEY)
        .suppress(Warning.STRICT_HASHCODE)
        .suppress(Warning.IDENTICAL_COPY_FOR_VERSIONED_ENTITY)
        .verify();
  }
}
