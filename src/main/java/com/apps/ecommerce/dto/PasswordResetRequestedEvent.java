package com.apps.ecommerce.dto;

/** Published once a reset token is committed, so the email is sent outside the transaction. */
public record PasswordResetRequestedEvent(String email, String token) {
}
