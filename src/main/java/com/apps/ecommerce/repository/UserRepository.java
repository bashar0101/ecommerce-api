package com.apps.ecommerce.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.apps.ecommerce.entity.User;

public interface UserRepository extends JpaRepository<User, UUID> {

}
