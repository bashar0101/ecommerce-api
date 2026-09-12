package com.apps.ecommerce.config;

import java.util.List;

import com.apps.ecommerce.security.JwtAuthFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
// import org.springframework.context.annotation.aspectj.EnableSpringConfigured;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import lombok.RequiredArgsConstructor;

@Configuration
// @EnableSpringConfigured
@RequiredArgsConstructor
public class SecurityConfig {

        private final JwtAuthFilter jwtAuthFilter;

        /**
         * Which browser origins may call this API. A comma separated list, so Render
         * can add the deployed front end without a rebuild. Anything not listed here
         * is blocked by the browser.
         */
        @Value("${app.cors.allowed-origins}")
        private String allowedOrigins;

        @Bean
        public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
                http.csrf(csrf -> csrf.disable())
                                // Without this the API sends no Access-Control-Allow-Origin header and
                                // every browser call from the front end fails, even though curl works -
                                // curl does not enforce CORS, browsers do. It also puts the CORS filter
                                // ahead of the auth rules, so the preflight OPTIONS request (which
                                // carries no Authorization header) is answered instead of 401.
                                .cors(Customizer.withDefaults())
                                .exceptionHandling(e -> e
                                                .authenticationEntryPoint(
                                                                new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                                .sessionManagement(s -> s
                                                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                                .authorizeHttpRequests(auth -> auth
                                                .requestMatchers("/actuator/health").permitAll()
                                                .requestMatchers("/api/v1/auth/**").permitAll()
                                                .requestMatchers(HttpMethod.GET, "/api/v1/products/**").permitAll()
                                                .requestMatchers("/api/v1/products/**").hasRole("ADMIN")
                                                .anyRequest().authenticated())
                                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
                return http.build();
        }

        @Bean
        public CorsConfigurationSource corsConfigurationSource() {
                CorsConfiguration config = new CorsConfiguration();
                // Patterns, not plain origins, so a wildcard like https://*.vercel.app can
                // be added later for preview deployments.
                config.setAllowedOriginPatterns(List.of(allowedOrigins.split("\s*,\s*")));
                config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
                // The front end sends Authorization and Content-Type. Listing them is
                // required: a header the browser does not see here makes the preflight fail.
                config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
                // false because the token travels in the Authorization header, not a cookie.
                // Cookies would need this true AND exact origins, no wildcards.
                config.setAllowCredentials(false);
                // Cache the preflight for an hour so the browser stops asking before
                // every single request.
                config.setMaxAge(3600L);

                UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
                source.registerCorsConfiguration("/**", config);
                return source;
        }

        @Bean
        public PasswordEncoder passwordEncoder() {
                return new BCryptPasswordEncoder();
        }

        @Bean
        public AuthenticationManager authManager(
                        AuthenticationConfiguration config) throws Exception {
                return config.getAuthenticationManager();
        }

}
