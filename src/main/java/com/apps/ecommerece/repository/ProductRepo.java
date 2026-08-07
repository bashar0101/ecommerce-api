package com.apps.ecommerece.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.apps.ecommerece.entity.Product;

public interface ProductRepo extends JpaRepository<Product, UUID> {

    public Optional<Product> findByname(String name);
}
