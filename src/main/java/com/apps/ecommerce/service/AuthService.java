package com.apps.ecommerce.service;

import java.time.LocalDateTime;
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
import com.apps.ecommerce.exception.DuplicateResourceException;
import com.apps.ecommerce.exception.InvalidTokenException;
import com.apps.ecommerce.repository.UserRepository;
import com.apps.ecommerce.repository.VerificationTokenRepository;
import com.apps.ecommerce.security.JwtService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthService {

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
        newUser.setRole(user.role());
        newUser.setEnabled(false);
        newUser.setCreatedAt(LocalDateTime.now());
        userRepository.save(newUser);

        VerificationToken token = new VerificationToken();
        token.setToken(UUID.randomUUID().toString());
        token.setUser(newUser);
        token.setExpiresAt(LocalDateTime.now().plusHours(24));
        tokenRepository.save(token);

        events.publishEvent(new UserRegisteredEvent(newUser.getId(), newUser.getEmail(), token.getToken()));

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

        if (token.getUsedAt() != null)
            throw new InvalidTokenException("Link already used");
        if (token.getExpiresAt().isBefore(LocalDateTime.now()))
            throw new InvalidTokenException("Link expired, please request a new one");

        User user = token.getUser();
        user.setEnabled(true);
        token.setUsedAt(LocalDateTime.now());
    }

    @Transactional
    public void resendVerification(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new InvalidTokenException("No account found for this email"));

        if (user.isEnabled()) {
            throw new InvalidTokenException("Account is already verified");
        }

        VerificationToken token = new VerificationToken();
        token.setToken(UUID.randomUUID().toString());
        token.setUser(user);
        token.setExpiresAt(LocalDateTime.now().plusHours(24));
        tokenRepository.save(token);

        events.publishEvent(new UserRegisteredEvent(user.getId(), user.getEmail(), token.getToken()));
    }
}
