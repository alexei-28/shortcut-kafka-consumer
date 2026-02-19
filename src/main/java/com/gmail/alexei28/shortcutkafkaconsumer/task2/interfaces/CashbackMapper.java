package com.gmail.alexei28.shortcutkafkaconsumer.task2.interfaces;

import com.gmail.alexei28.shortcutkafkaconsumer.task2.dto.CashbackDto;
import com.gmail.alexei28.shortcutkafkaconsumer.task2.entity.Cashback;
import org.mapstruct.Mapper;

// Чтобы маппер стал Spring-бином
@Mapper(componentModel = "spring")
public interface CashbackMapper {
  CashbackDto toDto(Cashback entity);

  Cashback toEntity(CashbackDto dto);
}
