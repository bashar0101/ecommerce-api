package com.apps.ecommerece.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.apps.ecommerece.entity.Order;

public interface OrderRepo extends JpaRepository<Order, UUID> {

}
