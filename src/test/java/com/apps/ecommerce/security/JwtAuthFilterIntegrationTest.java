package com.apps.ecommerce.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.apps.ecommerce.dto.LoginRequest;
import com.apps.ecommerce.dto.UserCreateRequest;
import com.apps.ecommerce.entity.User;
import com.apps.ecommerce.repository.PasswordResetTokenRepository;
import com.apps.ecommerce.repository.UserRepository;
import com.apps.ecommerce.repository.VerificationTokenRepository;
import com.apps.ecommerce.service.AuthService;

/**
 * Every other security test avoids the JWT filter: OrderControllerTest disables
 * the filters, OrderSecurityIntegrationTest uses @WithMockUser. That left a hole
 * big enough for a ClassCastException in JwtAuthFilter to make EVERY protected
 * endpoint answer 401 while all 34 tests stayed green.
 *
 * These tests use a genuine token from AuthService.login and send it through the
 * real filter chain, so that hole is closed.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class JwtAuthFilterIntegrationTest {

    private static final String EMAIL = "jwtfilter@example.com";
    private static final String PASSWORD = "Password123";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AuthService authService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private VerificationTokenRepository tokenRepository;
    @Autowired
    private PasswordResetTokenRepository resetTokenRepository;

    @MockitoBean
    private JavaMailSender mailSender;

    @BeforeEach
    void cleanUp() {
        resetTokenRepository.deleteAll();
        tokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    /** Registers, verifies, logs in, and returns a real signed token. */
    private String tokenForVerifiedUser() {
        authService.register(new UserCreateRequest("Jwt", "Filter", EMAIL, PASSWORD));
        authService.verify(tokenRepository.findAll().get(0).getToken());
        return authService.login(new LoginRequest(EMAIL, PASSWORD));
    }

    @Test
    @DisplayName("a real token opens a protected endpoint")
    void validTokenIsAccepted() throws Exception {
        String jwt = tokenForVerifiedUser();

        mockMvc.perform(get("/api/v1/orders").header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("no token means 401")
    void noTokenIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/orders"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("a token that is not signed by us is rejected")
    void forgedTokenIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/orders").header("Authorization", "Bearer not.a.real.token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("disabling the account stops the token working immediately")
    void disablingTheUserRevokesTheToken() throws Exception {
        String jwt = tokenForVerifiedUser();

        User user = userRepository.findByEmail(EMAIL).orElseThrow();
        user.setEnabled(false);
        userRepository.save(user);

        // No waiting for the 24h expiry: the filter reloads the user every request.
        mockMvc.perform(get("/api/v1/orders").header("Authorization", "Bearer " + jwt))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("changing the credentials revokes tokens issued before the change")
    void credentialsChangedAtRevokesOlderTokens() throws Exception {
        String jwt = tokenForVerifiedUser();

        // Set it in the future so the just-issued token is certainly older. A JWT
        // iat has second precision, so "now" would be ambiguous within the same second.
        User user = userRepository.findByEmail(EMAIL).orElseThrow();
        user.setCredentialsChangedAt(LocalDateTime.now().plusMinutes(5));
        userRepository.save(user);

        mockMvc.perform(get("/api/v1/orders").header("Authorization", "Bearer " + jwt))
                .andExpect(status().isUnauthorized());
    }
}
