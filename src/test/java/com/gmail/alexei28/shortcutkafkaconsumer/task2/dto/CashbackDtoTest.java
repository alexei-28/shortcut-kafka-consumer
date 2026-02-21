package com.gmail.alexei28.shortcutkafkaconsumer.task2.dto;

import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;

class CashbackDtoTest {
  @Test
  void simpleEqualsContract() {
    EqualsVerifier.simple().forClass(CashbackDto.class).verify();
  }
}
