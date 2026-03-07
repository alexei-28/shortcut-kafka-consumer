package com.gmail.alexei28.shortcutkafkaconsumer.task5.dto;

import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;

class UserDtoTest {
  @Test
  void simpleEqualsContract() {
    EqualsVerifier.simple().forClass(UserDto.class).verify();
  }
}
