package com.apps.ecommerce.service;

import org.springframework.stereotype.Service;

import com.apps.ecommerce.entity.User;
import com.apps.ecommerce.enums.Role;
import com.apps.ecommerce.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }

    public User save(User user) {
        return userRepository.save(user);
    }

    public void updateRole(String email, String role) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found with email: " + email));
        user.setRole(Role.valueOf(role));
        userRepository.save(user);
    }

}
