package com.apps.ecommerce.service;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.apps.ecommerce.dto.LoginRequest;
import com.apps.ecommerce.dto.UserCreateRequest;
import com.apps.ecommerce.dto.UserCreateResponse;
import com.apps.ecommerce.dto.UserRegisteredEvent;
import com.apps.ecommerce.entity.User;
import com.apps.ecommerce.entity.VerificationToken;
import com.apps.ecommerce.enums.Role;
import com.apps.ecommerce.exception.DuplicateResourceException;
import com.apps.ecommerce.exception.InvalidTokenException;
import com.apps.ecommerce.repository.UserRepository;
import com.apps.ecommerce.repository.VerificationTokenRepository;
import com.apps.ecommerce.security.JwtService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private static final long TOKEN_TTL_HOURS = 24;

    /** A resend inside this window is ignored, so /resend cannot be used to flood an inbox. */
    private static final long RESEND_COOLDOWN_MINUTES = 5;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authManager;
    private final VerificationTokenRepository tokenRepository;
    private final ApplicationEventPublisher events;

    @Transactional
    public UserCreateResponse register(UserCreateRequest user) {
        if (userRepository.existsByEmail(user.email())) {
            throw new DuplicateResourceException("email already registered");
        }
        User newUser = new User();
        newUser.setEmail(user.email());
        newUser.setFirstName(user.firstName());
        newUser.setLastName(user.lastName());
        newUser.setPassword(passwordEncoder.encode(user.password()));
        // Server decides, never the caller. /register is permitAll, so honouring a
        // client-supplied role would let anyone register themselves as an admin.
        // Promote a real admin with SQL: UPDATE users SET role='ADMIN' WHERE email=...
        newUser.setRole(Role.USER);
        newUser.setEnabled(false);
        newUser.setCreatedAt(LocalDateTime.now());
        userRepository.save(newUser);

        issueToken(newUser);

        return new UserCreateResponse(
                newUser.getEmail(),
                newUser.getFirstName(),
                newUser.getLastName(),
                newUser.getRole(),
                newUser.getCreatedAt());
    }

    public String login(LoginRequest loginRequest) {
        authManager
                .authenticate(new UsernamePasswordAuthenticationToken(loginRequest.email(), loginRequest.password()));

        return jwtService.generateToken(userRepository.findByEmail(loginRequest.email()));
    }

    @Transactional
    public void verify(String tokenValue) {
        VerificationToken token = tokenRepository.findByToken(tokenValue)
                .orElseThrow(() -> new InvalidTokenException("Invalid activation link"));

        User user = token.getUser();

        // Checked before usedAt on purpose. Mail scanners (Outlook SafeLinks, Gmail)
        // fetch the link when the message arrives, so the token is often already
        // consumed by the time the human clicks. The account is verified either way,
        // so report success rather than an error the user cannot act on.
        if (user.isEnabled()) {
            return;
        }

        if (token.getUsedAt() != null)
            throw new InvalidTokenException("Link already used");
        if (token.getExpiresAt().isBefore(LocalDateTime.now()))
            throw new InvalidTokenException("Link expired, please request a new one");

        user.setEnabled(true);
        invalidateOutstandingTokens(user);
    }

    /**
     * Deliberately silent about what happened. Returning different answers for
     * "no such account", "already verified" and "sent" would let anyone probe
     * which addresses are registered, so every outcome looks identical from
     * outside and the detail only goes to the log.
     */
    @Transactional
    public void resendVerification(String email) {
        Optional<User> found = userRepository.findByEmail(email);
        if (found.isEmpty()) {
            log.debug("Resend requested for unknown address");
            return;
        }

        User user = found.get();
        if (user.isEnabled()) {
            log.debug("Resend requested for an already verified account");
            return;
        }

        boolean withinCooldown = tokenRepository.findFirstByUserOrderByCreatedAtDesc(user)
                .filter(t -> t.getCreatedAt().isAfter(LocalDateTime.now().minusMinutes(RESEND_COOLDOWN_MINUTES)))
                .isPresent();
        if (withinCooldown) {
            log.debug("Resend ignored, still inside the {} minute cooldown", RESEND_COOLDOWN_MINUTES);
            return;
        }

        issueToken(user);
    }

    /**
     * Mints a fresh activation token and hands the email off to the listener.
     * Any token still outstanding for this user is retired first, so only the
     * newest link ever works.
     */
    private void issueToken(User user) {
        invalidateOutstandingTokens(user);

        VerificationToken token = new VerificationToken();
        token.setToken(UUID.randomUUID().toString());
        token.setUser(user);
        token.setExpiresAt(LocalDateTime.now().plusHours(TOKEN_TTL_HOURS));
        tokenRepository.save(token);

        events.publishEvent(new UserRegisteredEvent(user.getEmail(), token.getToken()));
    }

    /** Marks every unused token for this user as consumed, so old links stop working. */
    private void invalidateOutstandingTokens(User user) {
        LocalDateTime now = LocalDateTime.now();
        tokenRepository.findAllByUserAndUsedAtIsNull(user)
                .forEach(t -> t.setUsedAt(now));
    }
}
