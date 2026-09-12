package com.apps.ecommerce.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.apps.ecommerce.dto.PasswordResetRequestedEvent;
import com.apps.ecommerce.service.MailService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Same shape as UserRegisteredListener, and for the same two reasons:
 * AFTER_COMMIT so a rolled-back request never emails a live token, and @Async so
 * a slow mail provider does not hold up the HTTP response.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PasswordResetListener {

    private final MailService mailService;

    @Value("${app.base-url}")
    private String baseUrl;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPasswordResetRequested(PasswordResetRequestedEvent event) {
        // No clickable link: resetting takes a POST with the new password, which a
        // browser cannot do from a URL. A real frontend would link to its own page
        // and carry the token in the query string.
        String body = """
                Someone asked to reset the password for this account.

                Reset token: %s

                POST it to %s/api/v1/auth/reset-password together with your new password:
                  {"token": "%s", "newPassword": "..."}

                The token is valid for one hour and can be used once.
                If this was not you, ignore this email — nothing has changed yet.
                """.formatted(event.token(), baseUrl, event.token());

        try {
            mailService.send(event.email(), "Reset your password", body);
        } catch (Exception ex) {
            log.error("Could not send password reset mail to {}", event.email(), ex);
        }
    }
}
