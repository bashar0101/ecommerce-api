package com.apps.ecommerce.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.apps.ecommerce.dto.LoginRequest;
import com.apps.ecommerce.dto.ResendVerificationRequest;
import com.apps.ecommerce.dto.UserCreateRequest;
import com.apps.ecommerce.dto.UserCreateResponse;
import com.apps.ecommerce.service.AuthService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<UserCreateResponse> register(@Valid @RequestBody UserCreateRequest user) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(user));
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, String>> login(@Valid @RequestBody LoginRequest request) {
        String token = authService.login(request);
        return ResponseEntity.ok(Map.of("token", token));
    }

    @GetMapping("/verify")
    public ResponseEntity<Map<String, String>> verify(@RequestParam String token) {
        authService.verify(token);
        return ResponseEntity.ok(Map.of("message", "Account verified successfully"));
    }

    /**
     * Always answers the same way, whatever happened inside. Telling the caller
     * apart "no such account" from "already verified" from "sent" would turn this
     * public endpoint into a way to discover which addresses are registered.
     */
    @PostMapping("/resend")
    public ResponseEntity<Map<String, String>> resend(@Valid @RequestBody ResendVerificationRequest request) {
        authService.resendVerification(request.email());
        return ResponseEntity.ok(Map.of("message", "If that address needs verification, an email has been sent"));
    }

    // @GetMapping("/test-mail")
    // public String testMail() {
    // String to = "basharhas1999@gmail.com";
    // mailService.send(to, "Hello", "It works.");
    // return "sent to " + to;
    // }
}
