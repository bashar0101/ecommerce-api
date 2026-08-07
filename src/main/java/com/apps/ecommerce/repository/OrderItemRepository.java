package com.apps.ecommerce.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.apps.ecommerce.entity.OrderItem;

public interface OrderItemRepository extends JpaRepository<OrderItem, UUID> {

}
