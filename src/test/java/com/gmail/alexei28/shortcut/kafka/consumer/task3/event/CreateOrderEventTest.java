package com.gmail.alexei28.shortcut.kafka.consumer.task3.event;

import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;

class CreateOrderEventTest {

  @Test
  void simpleEqualsContract() {
    EqualsVerifier.simple().forClass(CreateOrderEvent.class).verify();
  }
}
