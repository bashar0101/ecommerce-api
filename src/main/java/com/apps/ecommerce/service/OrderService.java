package com.apps.ecommerce.service;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;

import com.apps.ecommerce.dto.OrderCreateRequest;
import com.apps.ecommerce.dto.OrderResponse;
import com.apps.ecommerce.entity.Order;
import com.apps.ecommerce.enums.OrderStatus;
import com.apps.ecommerce.repository.OrderRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;

    public OrderResponse create(OrderCreateRequest dto) {
        Order newOrder = new Order();
        newOrder.setCreatedAt(LocalDateTime.now());
        newOrder.setStatus(OrderStatus.PENDING);
        newOrder.setTotalPrice(null);
        return null;

    }

}
