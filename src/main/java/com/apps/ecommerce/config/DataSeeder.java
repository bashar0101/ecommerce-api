package com.apps.ecommerce.config;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.apps.ecommerce.entity.User;
import com.apps.ecommerce.enums.Role;
import com.apps.ecommerce.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Inserts a test user on startup so the API can be exercised without a
 * registration endpoint. Disable with app.seed.enabled=false.
 */
@Configuration
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class DataSeeder {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.seed.user.email:test@example.com}")
    private String email;

    @Value("${app.seed.user.password:password123}")
    private String password;

    @Value("${app.seed.user.id:11111111-1111-1111-1111-111111111111}")
    private UUID id;

    @Bean
    CommandLineRunner seedTestUser() {
        return args -> {
            User user = userRepository.findByEmail(email)
                    .orElseGet(() -> {
                        User u = new User();
                        u.setId(id);
                        u.setEmail(email);
                        u.setPassword(passwordEncoder.encode(password));
                        u.setRole(Role.USER);
                        u.setEnabled(true);
                        return userRepository.save(u);
                    });

            log.info("""

                    ============== TEST USER ==============
                     id       : {}
                     email    : {}
                     password : {}
                    =======================================""",
                    user.getId(), user.getEmail(), password);
        };
    }

}
