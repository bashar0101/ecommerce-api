package com.apps.ecommerce.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.apps.ecommerce.dto.OrderCreateRequest;
import com.apps.ecommerce.dto.OrderItemRequest;
import com.apps.ecommerce.dto.OrderResponse;
import com.apps.ecommerce.entity.Product;
import com.apps.ecommerce.entity.User;
import com.apps.ecommerce.enums.Role;
import com.apps.ecommerce.exception.InsufficientStockException;
import com.apps.ecommerce.repository.OrderRepository;
import com.apps.ecommerce.repository.ProductRepository;
import com.apps.ecommerce.repository.UserRepository;

@SpringBootTest
@ActiveProfiles("test")
public class OrderServiceIntegrationTest {

    @Autowired
    private OrderService orderService;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private OrderRepository orderRepository;

    private UUID userId;
    private UUID laptopId;
    private UUID mouseId;

    @BeforeEach
    void setUp() {
        orderRepository.deleteAll();
        productRepository.deleteAll();
        userRepository.deleteAll();

        User user = new User();
        user.setEmail("test@example.com");
        user.setPassword("x");
        user.setRole(Role.USER); // use your own enum value
        userId = userRepository.save(user).getId();

        Product laptop = new Product();
        laptop.setName("Laptop");
        laptop.setPrice(new BigDecimal("1000.00"));
        laptop.setStock(10);
        laptop.setDescription("test");
        laptopId = productRepository.save(laptop).getId();

        Product mouse = new Product();
        mouse.setName("Mouse");
        mouse.setPrice(new BigDecimal("20.00"));
        mouse.setStock(1);
        mouse.setDescription("test");
        mouseId = productRepository.save(mouse).getId();
    }

    @Test
    @DisplayName("when one item fails, the other item's stock is not changed")
    void rollsBackEverythingWhenOneItemFails() {
        OrderCreateRequest request = new OrderCreateRequest(List.of(
                new OrderItemRequest(laptopId, 2), // fine
                new OrderItemRequest(mouseId, 5))); // fails

        assertThrows(InsufficientStockException.class,
                () -> orderService.create(userId, request));

        assertEquals(10, productRepository.findById(laptopId).get().getStock());
        assertEquals(0, orderRepository.count());
    }

    @Test
    @DisplayName("a successful order reduces the stock in the database")
    void reducesStockInDatabase() {
        OrderCreateRequest request = new OrderCreateRequest(
                List.of(new OrderItemRequest(laptopId, 2)));

        OrderResponse response = orderService.create(userId, request);

        assertEquals(1, orderRepository.count());
        assertEquals(8, productRepository.findById(laptopId).get().getStock());
        assertEquals(0, new BigDecimal("2000.00").compareTo(response.totalPrice()));
    }
}
