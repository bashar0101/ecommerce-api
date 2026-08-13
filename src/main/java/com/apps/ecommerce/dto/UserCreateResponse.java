package com.apps.ecommerce.dto;

import java.time.LocalDateTime;

import com.apps.ecommerce.enums.Role;

public record UserCreateResponse(
        String email,
        String firstName,
        String lastName,
        Role role,
        LocalDateTime createdAt) {

}
