package com.gmail.alexei28.shortcutkafkaconsumer.task3.mapper;

import com.gmail.alexei28.shortcutkafkaconsumer.task3.event.CreateOrderEvent;
import com.gmail.alexei28.shortcutkafkaconsumer.task3.operation.AccountOperation;
import java.time.OffsetDateTime;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", imports = OffsetDateTime.class)
public interface CreateOrderEventAccountOperationMapping {
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "createdAt", expression = "java(OffsetDateTime.now())")
  AccountOperation toOperation(CreateOrderEvent createOrderEvent);
}
