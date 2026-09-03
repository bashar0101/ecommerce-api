package com.apps.ecommerce.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Deliberately a separate table from VerificationToken rather than one table with
 * a "type" column. They look alike but mean different things: one proves an
 * address is real, the other authorises a password change. Keeping them apart
 * means a leaked activation link can never be replayed as a password reset.
 *
 * Shorter lived than a verification token — a reset link is a live credential for
 * the account, so it should stop being one quickly.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class PasswordResetToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String token;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private User user;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    /** Null until used. Also set to retire a token superseded by a newer request. */
    private LocalDateTime usedAt;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
