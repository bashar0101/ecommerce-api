package com.apps.ecommerce.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.apps.ecommerce.entity.PasswordResetToken;
import com.apps.ecommerce.entity.User;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

    Optional<PasswordResetToken> findByToken(String token);

    List<PasswordResetToken> findAllByUserAndUsedAtIsNull(User user);

    Optional<PasswordResetToken> findFirstByUserOrderByCreatedAtDesc(User user);
}
