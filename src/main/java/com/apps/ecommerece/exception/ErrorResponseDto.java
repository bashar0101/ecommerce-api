package com.apps.ecommerece.exception;

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
