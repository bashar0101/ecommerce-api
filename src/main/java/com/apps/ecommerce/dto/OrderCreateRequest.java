package com.apps.ecommerce.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

public record OrderCreateRequest(
        @NotEmpty @Valid List<OrderItemRequest> items) {

}
