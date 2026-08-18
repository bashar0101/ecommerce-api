package com.apps.ecommerce.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.authentication.DisabledException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.apps.ecommerce.dto.LoginRequest;
import com.apps.ecommerce.dto.UserCreateRequest;
import com.apps.ecommerce.entity.User;
import com.apps.ecommerce.entity.VerificationToken;
import com.apps.ecommerce.enums.Role;
import com.apps.ecommerce.exception.InvalidTokenException;
import com.apps.ecommerce.repository.UserRepository;
import com.apps.ecommerce.repository.VerificationTokenRepository;

@SpringBootTest
@ActiveProfiles("test")
public class AuthServiceIntegrationTest {

    private static final String EMAIL = "new1@example.com";

    @Autowired
    private AuthService authService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private VerificationTokenRepository tokenRepository;

    /** Keeps the tests off a real SMTP server; the listener still fires. */
    @MockitoBean
    private JavaMailSender mailSender;

    /**
     * Every test registers the same address, and the token table is asserted on by
     * count, so both have to start empty. Tokens go first — they hold a foreign key
     * to users, and deleting the parent first is a constraint violation.
     */
    @BeforeEach
    void cleanUp() {
        tokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    private User register() {
        authService.register(new UserCreateRequest("New", "User", EMAIL, "password123", Role.USER));
        return userRepository.findByEmail(EMAIL).orElseThrow();
    }

    private String onlyToken() {
        return tokenRepository.findAll().get(0).getToken();
    }

    @Test
    @DisplayName("a new user is created disabled, with a token")
    void registerCreatesDisabledUser() {
        User user = register();

        assertFalse(user.isEnabled());
        assertEquals(1, tokenRepository.count());
    }

    @Test
    @DisplayName("a valid token enables the user and is consumed")
    void verifyEnablesUser() {
        register();
        String token = onlyToken();

        authService.verify(token);

        assertTrue(userRepository.findByEmail(EMAIL).orElseThrow().isEnabled());
        assertNotNull(tokenRepository.findByToken(token).orElseThrow().getUsedAt(),
                "the token should be marked used");
    }

    @Test
    @DisplayName("verifying a second time is a safe no-op, not an error")
    void verifyingTwiceIsSafe() {
        register();
        String token = onlyToken();

        authService.verify(token);
        authService.verify(token); // mail scanners prefetch the link; this must not throw

        assertTrue(userRepository.findByEmail(EMAIL).orElseThrow().isEnabled());
    }

    @Test
    @DisplayName("verifying retires every other outstanding token for that user")
    void verifyRetiresOtherTokens() {
        User user = register();
        String token = onlyToken();

        // Stands in for a token from an earlier resend. Created directly rather than
        // through resendVerification, which would be swallowed by the cooldown.
        VerificationToken stale = new VerificationToken();
        stale.setToken("stale-token");
        stale.setUser(user);
        stale.setExpiresAt(LocalDateTime.now().plusHours(24));
        tokenRepository.save(stale);

        authService.verify(token);

        assertTrue(tokenRepository.findAllByUserAndUsedAtIsNull(user).isEmpty(),
                "no unused token should survive verification");
    }

    @Test
    @DisplayName("an unknown token is rejected")
    void unknownTokenIsRejected() {
        assertThrows(InvalidTokenException.class, () -> authService.verify("not-a-real-token"));
    }

    @Test
    @DisplayName("an expired token is rejected")
    void expiredTokenIsRejected() {
        register();
        VerificationToken token = tokenRepository.findAll().get(0);
        token.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        tokenRepository.save(token);

        assertThrows(InvalidTokenException.class, () -> authService.verify(token.getToken()));
        assertFalse(userRepository.findByEmail(EMAIL).orElseThrow().isEnabled());
    }

    @Test
    @DisplayName("a disabled user cannot log in")
    void disabledUserCannotLogin() {
        register();

        assertThrows(DisabledException.class,
                () -> authService.login(new LoginRequest(EMAIL, "password123")));
    }

    @Test
    @DisplayName("resend stays silent for an unknown address")
    void resendIsSilentForUnknownAddress() {
        // No throw, and nothing written — the endpoint must not reveal who has an
        // account.
        authService.resendVerification("nobody@example.com");

        assertEquals(0, tokenRepository.count());
    }

    @Test
    @DisplayName("a banned user cannot re-enable themselves with the old link")
    void bannedUserCannotReactivate() {
        User user = register();
        String token = onlyToken();
        authService.verify(token);

        user.setEnabled(false); // an admin bans them
        userRepository.save(user);

        assertThrows(InvalidTokenException.class, () -> authService.verify(token));
        assertFalse(userRepository.findByEmail(EMAIL).orElseThrow().isEnabled());
    }
}
