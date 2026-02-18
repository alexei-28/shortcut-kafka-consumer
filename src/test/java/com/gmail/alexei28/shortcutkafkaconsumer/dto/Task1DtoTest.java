package com.gmail.alexei28.shortcutkafkaconsumer.dto;

import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;

class Task1DtoTest {

  @Test
  void simpleEqualsContract() {
    EqualsVerifier.simple().forClass(Task1Dto.class).verify();
  }
}
