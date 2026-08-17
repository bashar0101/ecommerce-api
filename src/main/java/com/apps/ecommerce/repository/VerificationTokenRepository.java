package com.apps.ecommerce.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.apps.ecommerce.entity.User;
import com.apps.ecommerce.entity.VerificationToken;

public interface VerificationTokenRepository extends JpaRepository<VerificationToken, UUID> {

    Optional<VerificationToken> findByToken(String token);

    /** Every token for this user that has not been consumed yet. */
    List<VerificationToken> findAllByUserAndUsedAtIsNull(User user);

    /** The most recently issued token for this user, used for the resend cooldown. */
    Optional<VerificationToken> findFirstByUserOrderByCreatedAtDesc(User user);

}
