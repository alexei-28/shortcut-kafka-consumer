package com.gmail.alexei28.shortcutkafkaconsumer.intrefaces;

import com.gmail.alexei28.shortcutkafkaconsumer.dto.Task1Dto;
import com.gmail.alexei28.shortcutkafkaconsumer.entity.Task1;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring") // Чтобы маппер стал Spring-бином
public interface TaskMapper {
  // Entity -> DTO (для продюсера)

  // Если имена полей совпадают (number, content, receivedAt),
  // MapStruct свяжет их автоматически.
  Task1Dto toDto(Task1 entity);

  // DTO -> Entity (для консьюмера)
  // Если бы имена отличались, мы бы писали так:
  // @Mapping(source = "content", target = "textContent")
  Task1 toEntity(Task1Dto dto);
}
