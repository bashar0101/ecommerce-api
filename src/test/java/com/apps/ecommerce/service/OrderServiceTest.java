package com.apps.ecommerce.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.apps.ecommerce.dto.OrderCreateRequest;
import com.apps.ecommerce.dto.OrderItemRequest;
import com.apps.ecommerce.dto.OrderResponse;
import com.apps.ecommerce.entity.Order;
import com.apps.ecommerce.entity.Product;
import com.apps.ecommerce.entity.User;
import com.apps.ecommerce.enums.OrderStatus;
import com.apps.ecommerce.repository.OrderRepository;
import com.apps.ecommerce.repository.ProductRepository;
import com.apps.ecommerce.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
public class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private UserRepository userRepository;

    // @Mock makes a fake. @InjectMocks builds the real OrderService and pushes the
    // three fakes into it.
    @InjectMocks
    private OrderService orderService;

    @Test
    @DisplayName("throws when the stock is not enough, and saves nothing")
    void throwsWhenStockNotEnough() {
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        User user = new User();
        user.setId(userId);

        Product product = new Product();
        product.setId(productId);
        product.setName("Laptop");
        product.setStock(3);
        product.setPrice(new BigDecimal("100000"));

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(productRepository.findAllById(any())).thenReturn(List.of(product));

        OrderCreateRequest request = new OrderCreateRequest(List.of(new OrderItemRequest(productId, 1)));

        assertThrows(IllegalArgumentException.class, () -> orderService.create(userId, request));

        assertEquals(3, product.getStock());
        verify(orderRepository, never()).save(any());

    }

    @Test
    @DisplayName("calculates the total and reduces the stock")
    void createsOrderCorrectly() {
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        User user = new User();
        user.setId(userId);

        Product product = new Product();
        product.setId(productId);
        product.setName("Mouse");
        product.setPrice(new BigDecimal("25.50"));
        product.setStock(10);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(productRepository.findAllById(any())).thenReturn(List.of(product));
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

        OrderCreateRequest request = new OrderCreateRequest(
                List.of(new OrderItemRequest(productId, 2)));

        OrderResponse response = orderService.create(userId, request);

        assertEquals(0, new BigDecimal("51.00").compareTo(response.totalPrice()));
        assertEquals(8, product.getStock());
        assertEquals(OrderStatus.PENDING, response.status());
    }

}
