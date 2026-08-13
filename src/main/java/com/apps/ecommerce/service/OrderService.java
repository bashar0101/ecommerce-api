package com.apps.ecommerce.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.apps.ecommerce.dto.OrderCreateRequest;
import com.apps.ecommerce.dto.OrderItemRequest;
import com.apps.ecommerce.dto.OrderItemResponse;
import com.apps.ecommerce.dto.OrderResponse;
import com.apps.ecommerce.entity.Order;
import com.apps.ecommerce.entity.OrderItem;
import com.apps.ecommerce.entity.Product;
import com.apps.ecommerce.entity.User;
import com.apps.ecommerce.enums.OrderStatus;
import com.apps.ecommerce.exception.InsufficientStockException;
import com.apps.ecommerce.exception.ResourceNotFoundException;
import com.apps.ecommerce.repository.OrderRepository;
import com.apps.ecommerce.repository.ProductRepository;
import com.apps.ecommerce.repository.UserRepository;

import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OrderService {

        private final OrderRepository orderRepository;
        private final ProductRepository productRepository;
        private final UserRepository userRepository;

        @SuppressWarnings("null")
        @Transactional
        public OrderResponse create(String email, OrderCreateRequest dto) {
                User user = userRepository.findByEmail(email)
                                .orElseThrow(() -> new ResourceNotFoundException(User.class, email));

                Set<UUID> ids = dto.items().stream()
                                .map(OrderItemRequest::productId)
                                .collect(Collectors.toSet());

                Map<UUID, Product> products = productRepository.findAllById(ids).stream()
                                .collect(Collectors.toMap(Product::getId, p -> p));
                if (products.size() != ids.size()) {
                        ids.removeAll(products.keySet());
                        throw new ResourceNotFoundException(Product.class, ids.iterator().next());
                }

                Order newOrder = new Order();
                newOrder.setUser(user);
                newOrder.setCreatedAt(LocalDateTime.now());
                newOrder.setStatus(OrderStatus.PENDING);
                BigDecimal total = BigDecimal.ZERO;

                for (OrderItemRequest orderRequest : dto.items()) {
                        Product product = products.get(orderRequest.productId());

                        if (product.getStock() < orderRequest.quantity()) {
                                throw new InsufficientStockException(
                                                product.getName(), product.getStock(), orderRequest.quantity());
                        }

                        product.setStock(product.getStock() - orderRequest.quantity());

                        OrderItem item = new OrderItem();
                        item.setOrder(newOrder);
                        item.setProduct(product);
                        item.setQuantity(orderRequest.quantity());
                        item.setUnitPrice(product.getPrice());

                        newOrder.getItems().add(item);

                        total = total.add(
                                        item.getUnitPrice().multiply(BigDecimal.valueOf(orderRequest.quantity())));
                }

                newOrder.setTotalPrice(total);
                Order saved = orderRepository.save(newOrder);

                return toDto(saved);

        }

        // this where we user user id
        // @SuppressWarnings("null")
        // @Transactional
        // public OrderResponse create(UUID userId, OrderCreateRequest dto) {
        // User user = userRepository.findById(userId)
        // .orElseThrow(() -> new ResourceNotFoundException(User.class, userId));

        // Set<UUID> ids = dto.items().stream()
        // .map(OrderItemRequest::productId)
        // .collect(Collectors.toSet());

        // Map<UUID, Product> products = productRepository.findAllById(ids).stream()
        // .collect(Collectors.toMap(Product::getId, p -> p));
        // if (products.size() != ids.size()) {
        // ids.removeAll(products.keySet());
        // throw new ResourceNotFoundException(Product.class, ids.iterator().next());
        // }

        // Order newOrder = new Order();
        // newOrder.setUser(user);
        // newOrder.setCreatedAt(LocalDateTime.now());
        // newOrder.setStatus(OrderStatus.PENDING);
        // BigDecimal total = BigDecimal.ZERO;

        // for (OrderItemRequest orderRequest : dto.items()) {
        // Product product = products.get(orderRequest.productId());

        // if (product.getStock() < orderRequest.quantity()) {
        // throw new InsufficientStockException(
        // product.getName(), product.getStock(), orderRequest.quantity());
        // }

        // product.setStock(product.getStock() - orderRequest.quantity());

        // OrderItem item = new OrderItem();
        // item.setOrder(newOrder);
        // item.setProduct(product);
        // item.setQuantity(orderRequest.quantity());
        // item.setUnitPrice(product.getPrice());

        // newOrder.getItems().add(item);

        // total = total.add(
        // item.getUnitPrice().multiply(BigDecimal.valueOf(orderRequest.quantity())));
        // }

        // newOrder.setTotalPrice(total);
        // Order saved = orderRepository.save(newOrder);

        // return toDto(saved);

        // }

        public List<OrderResponse> getAllByUserId(UUID userId) {
                User user = userRepository.findById(userId)
                                .orElseThrow(() -> new ResourceNotFoundException(User.class, userId));

                return orderRepository.findAllByUser(user).stream()
                                .map(this::toDto)
                                .toList();
        }

        private OrderResponse toDto(Order order) {
                List<OrderItemResponse> items = order.getItems().stream()
                                .map(i -> new OrderItemResponse(
                                                i.getProduct().getId(),
                                                i.getProduct().getName(),
                                                i.getQuantity(),
                                                i.getUnitPrice()))
                                .toList();

                return new OrderResponse(
                                order.getId(), order.getStatus(), order.getTotalPrice(),
                                order.getCreatedAt(), items);
        }

}
