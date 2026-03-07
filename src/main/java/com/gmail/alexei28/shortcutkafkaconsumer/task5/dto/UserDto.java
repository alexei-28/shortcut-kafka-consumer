package com.gmail.alexei28.shortcutkafkaconsumer.task5.dto;

import java.util.Objects;
import java.util.UUID;

public class UserDto {
  private UUID userId;
  private String firstName;
  private String lastName;
  private String email;
  private String inn;
  private String address;

  public UUID getUserId() {
    return userId;
  }

  public void setUserId(UUID userId) {
    this.userId = userId;
  }

  public String getFirstName() {
    return firstName;
  }

  public void setFirstName(String firstName) {
    this.firstName = firstName;
  }

  public String getLastName() {
    return lastName;
  }

  public void setLastName(String lastName) {
    this.lastName = lastName;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public String getInn() {
    return inn;
  }

  public void setInn(String inn) {
    this.inn = inn;
  }

  public String getAddress() {
    return address;
  }

  public void setAddress(String address) {
    this.address = address;
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) return false;
    UserDto userDto = (UserDto) o;
    return Objects.equals(userId, userDto.userId)
        && Objects.equals(firstName, userDto.firstName)
        && Objects.equals(lastName, userDto.lastName)
        && Objects.equals(email, userDto.email)
        && Objects.equals(inn, userDto.inn)
        && Objects.equals(address, userDto.address);
  }

  @Override
  public int hashCode() {
    return Objects.hash(userId, firstName, lastName, email, inn, address);
  }

  @Override
  public String toString() {
    return "\nUserDto{"
        + ",\n userId = "
        + userId
        + ",\n firstName = '"
        + firstName
        + '\''
        + ",\n lastName = '"
        + lastName
        + '\''
        + ",\n email = '"
        + email
        + '\''
        + ",\n inn = '"
        + inn
        + '\''
        + ",\n address = '"
        + address
        + '\''
        + "\n"
        + '}';
  }
}
