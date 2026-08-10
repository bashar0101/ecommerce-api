package com.apps.ecommerce.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.apps.ecommerce.entity.Order;

public class OrderTest {
    @Test
    @DisplayName("two orders with the same id are equal")
    void sameIdMeansEqual() {
        UUID id = UUID.randomUUID();

        Order a = new Order();
        a.setId(id);

        Order b = new Order();
        b.setId(id);

        assertEquals(a, b);
    }
}
