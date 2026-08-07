package com.apps.ecommerce.exception;

import java.time.LocalDateTime;
import java.util.Map;

public record ErrorResponseDto(
        int status,
        String message,
        String error,
        LocalDateTime timestamp,
        Map<String, String> fields // only only for validation errors

) {

}
