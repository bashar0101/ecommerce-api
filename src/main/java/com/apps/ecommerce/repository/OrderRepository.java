package com.apps.ecommerce.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.apps.ecommerce.entity.Order;

public interface OrderRepository extends JpaRepository<Order, UUID> {

}
