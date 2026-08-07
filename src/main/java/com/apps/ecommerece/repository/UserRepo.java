package com.apps.ecommerece.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.apps.ecommerece.entity.User;

public interface UserRepo extends JpaRepository<User, UUID> {

}
