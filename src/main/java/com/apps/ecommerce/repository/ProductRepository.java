package com.apps.ecommerce.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.apps.ecommerce.entity.Product;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    public Optional<Product> findByName(String name);
}
