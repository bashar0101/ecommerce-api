package com.apps.ecommerce.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.apps.ecommerce.entity.OrderItem;
import com.apps.ecommerce.enums.OrderStatus;

public record OrderResponse(
        UUID id,
        OrderStatus status,
        LocalDateTime createdAt,
        List<OrderItem> items) {

}
