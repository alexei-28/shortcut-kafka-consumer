package com.gmail.alexei28.shortcutkafkaconsumer.task4.interfaces;

import com.gmail.alexei28.shortcutkafkaconsumer.task4.dto.UserDto;
import com.gmail.alexei28.shortcutkafkaconsumer.task4.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

// Чтобы маппер стал Spring-бином
@Mapper(componentModel = "spring")
public interface UserMapper {
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "status", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  User toEntity(UserDto dto);
}
