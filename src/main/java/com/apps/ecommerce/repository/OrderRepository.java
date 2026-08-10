package com.apps.ecommerce.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.apps.ecommerce.entity.Order;
import com.apps.ecommerce.entity.User;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    List<Order> findAllByUser(User user);

}
