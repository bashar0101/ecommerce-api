package com.apps.ecommerece.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record ProductResponseDto(
        UUID id,
        String name,
        BigDecimal price,
        Integer stock,
        String description) {

}
