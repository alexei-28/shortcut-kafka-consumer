package com.gmail.alexei28.shortcutkafkaconsumer.task3.interfaces;

import com.gmail.alexei28.shortcutkafkaconsumer.task3.domain.Transfer;
import com.gmail.alexei28.shortcutkafkaconsumer.task3.dto.TransferDto;
import org.mapstruct.InheritInverseConfiguration;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

// Чтобы маппер стал Spring-бином
@Mapper(componentModel = "spring")
public interface TransferMapper {
  // Когда создаёшь Kafka-событие — сгенерируй новый id сообщения.
  @Mapping(target = "eventId", expression = "java(UUID.randomUUID())")
  TransferDto toDto(Transfer entity);

  @InheritInverseConfiguration
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "status", ignore = true)
  @Mapping(target = "processedAt", ignore = true)
  Transfer toEntity(TransferDto dto);
}
