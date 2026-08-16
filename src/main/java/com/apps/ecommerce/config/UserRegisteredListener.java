package com.apps.ecommerce.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.apps.ecommerce.dto.UserRegisteredEvent;
import com.apps.ecommerce.service.MailService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserRegisteredListener {

    private final MailService mailService;

    @Value("${app.base-url}")
    private String baseUrl;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUserRegistered(UserRegisteredEvent event) {
        String link = baseUrl + "/api/v1/auth/verify?token=" + event.token();
        try {
            mailService.send(event.email(), "Activate your account",
                    "Click this link to activate your account:\n" + link);
        } catch (Exception ex) {
            log.error("Could not send activation mail to {}", event.email(), ex);
        }
    }

}
