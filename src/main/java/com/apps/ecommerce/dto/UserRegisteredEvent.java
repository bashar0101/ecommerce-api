package com.apps.ecommerce.dto;

/**
 * Published by AuthService once a user and their activation token are committed.
 * Carries only what the listener needs to build and send the email — it must not
 * hand over the entity, since the listener runs on another thread with no
 * transaction open.
 */
public record UserRegisteredEvent(
        String email, String token) {

}
