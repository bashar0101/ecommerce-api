package com.apps.ecommerce.service;

import java.time.LocalDateTime;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.apps.ecommerce.dto.LoginRequest;
import com.apps.ecommerce.dto.UserCreateRequest;
import com.apps.ecommerce.dto.UserCreateResponse;
import com.apps.ecommerce.entity.User;
import com.apps.ecommerce.exception.DuplicateResourceException;
import com.apps.ecommerce.repository.UserRepository;
import com.apps.ecommerce.security.JwtService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authManager;

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
        newUser.setEnabled(user.enabled());
        newUser.setCreatedAt(LocalDateTime.now());
        userRepository.save(newUser);

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
}
