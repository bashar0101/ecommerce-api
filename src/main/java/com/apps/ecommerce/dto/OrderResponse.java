package com.apps.ecommerce.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.apps.ecommerce.enums.OrderStatus;

public record OrderResponse(
                UUID id,
                OrderStatus status,
                BigDecimal totalPrice,
                LocalDateTime createdAt,
                List<OrderItemResponse> items) {

}
