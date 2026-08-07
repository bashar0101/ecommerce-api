package com.apps.ecommerce.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record ProductResponse(
                UUID id,
                String name,
                BigDecimal price,
                Integer stock,
                String description) {

}
