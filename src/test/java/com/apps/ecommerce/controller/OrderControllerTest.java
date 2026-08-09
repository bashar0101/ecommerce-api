package com.apps.ecommerce.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
// import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.apps.ecommerce.config.SecurityConfig;
import com.apps.ecommerce.dto.OrderCreateRequest;
import com.apps.ecommerce.dto.OrderItemRequest;
import com.apps.ecommerce.dto.OrderItemResponse;
import com.apps.ecommerce.dto.OrderResponse;
import com.apps.ecommerce.enums.OrderStatus;
import com.apps.ecommerce.exception.InsufficientStockException;
import com.apps.ecommerce.service.OrderService;

import tools.jackson.databind.ObjectMapper;

// @WebMvcTest does not pick up a plain @Configuration class, so the real filter
// chain (CSRF disabled, /api/v1/orders/** permitAll) has to be imported by hand.
// Without it Spring Security's defaults apply and every POST is rejected with 403.
@WebMvcTest(OrderController.class)
@Import(SecurityConfig.class)
public class OrderControllerTest {
        @Autowired
        private MockMvc mockMvc;
        @Autowired
        private ObjectMapper objectMapper;

        @MockitoBean
        private OrderService orderService; // org.springframework.test.context.bean.override.mockito.MockitoBean

        private final UUID userId = UUID.randomUUID();
        private final UUID productId = UUID.randomUUID();
        private final UUID productId2 = UUID.randomUUID();

        @Test
        @DisplayName("returns 201 and the order body")
        void returns201() throws Exception {
                OrderResponse response = new OrderResponse(
                                UUID.randomUUID(), OrderStatus.PENDING, new BigDecimal("51.00"),
                                LocalDateTime.now(),
                                List.of(new OrderItemResponse(productId, "Mouse", 2, new BigDecimal("25")),
                                                new OrderItemResponse(productId2, "Keyboard", 2,
                                                                new BigDecimal("25"))));

                when(orderService.create(any(), any())).thenReturn(response);
                System.out.println("test");

                String body = objectMapper.writeValueAsString(
                                new OrderCreateRequest(List.of(new OrderItemRequest(productId, 2),
                                                new OrderItemRequest(productId2, 2))));

                mockMvc.perform(post("/api/v1/orders")
                                .param("userId", userId.toString())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.totalPrice").value(51.00))
                                .andExpect(jsonPath("$.items[0].productName").value("Mouse"))
                                .andExpect(jsonPath("$.items[1].productName").value("Keyboard"));
        }

        @Test
        @DisplayName("returns 400 when quantity is zero")
        void returns400() throws Exception {
                String body = objectMapper.writeValueAsString(
                                new OrderCreateRequest(List.of(new OrderItemRequest(productId, 0))));

                mockMvc.perform(post("/api/v1/orders")
                                .param("userId", userId.toString())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                                .andExpect(status().isBadRequest());

                verify(orderService, never()).create(any(), any());
        }

        @Test
        @DisplayName("returns 409 when the stock is not enough")
        void returns409() throws Exception {
                when(orderService.create(any(), any()))
                                .thenThrow(new InsufficientStockException("Mouse", 1, 5));

                String body = objectMapper.writeValueAsString(
                                new OrderCreateRequest(List.of(new OrderItemRequest(productId, 5))));

                mockMvc.perform(post("/api/v1/orders")
                                .param("userId", userId.toString())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                                .andExpect(status().isConflict());
        }
}
