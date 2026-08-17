package com.apps.ecommerce.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.apps.ecommerce.dto.OrderCreateRequest;
import com.apps.ecommerce.dto.OrderResponse;
import com.apps.ecommerce.service.OrderService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderService orderService;

    @PostMapping("")
    public ResponseEntity<OrderResponse> createOrder(@AuthenticationPrincipal UserDetails principal,
            @Valid @RequestBody OrderCreateRequest dto) {

        // this method using userID
        // String email = principal.getUsername();
        // UUID userId = userRepository.findByEmail(email).map(User::getId)
        // .orElseThrow(() -> new RuntimeException("User not found"));

        // OrderResponse newOrder = orderService.create(userId, dto);

        OrderResponse newOrder = orderService.create(principal.getUsername(), dto);

        return ResponseEntity.created(URI.create("/api/v1/orders/" + newOrder.id())).body(newOrder);
    }

    @GetMapping("")
    public ResponseEntity<List<OrderResponse>> getMyOrders(@AuthenticationPrincipal UserDetails principal) {
        return ResponseEntity.ok(orderService.getAllByEmail(principal.getUsername()));
    }

}
