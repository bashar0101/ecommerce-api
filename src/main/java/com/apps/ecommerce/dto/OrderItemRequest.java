package com.apps.ecommerce.dto;

import java.util.UUID;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record OrderItemRequest(
        @NotNull UUID productId,
        @NotNull @Min(1) Integer quantity) {

}
