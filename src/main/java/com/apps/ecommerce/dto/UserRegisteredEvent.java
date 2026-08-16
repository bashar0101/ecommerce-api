package com.apps.ecommerce.dto;

import java.util.UUID;

public record UserRegisteredEvent(
        UUID userId, String email, String token) {

}
