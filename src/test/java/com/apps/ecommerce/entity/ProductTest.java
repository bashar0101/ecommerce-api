package com.apps.ecommerce.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class ProductTest {

    @Test
    @DisplayName("two products with the same id are equal")
    void sameIdMeansEqual() {

        UUID id = UUID.randomUUID(); // Arrange
        Product a = new Product();
        a.setId(id);
        a.setName("a");

        Product b = new Product();
        b.setId(id);
        b.setName("b");

        assertEquals(a, b); // Assert
    }

    @Test
    @DisplayName("two products with different ids are not equal")
    void differentIdMeansNotEqual() {

        Product a = new Product();
        a.setId(UUID.randomUUID());
        a.setName("a");

        Product b = new Product();
        b.setId(UUID.randomUUID());
        b.setName("b");

        assertNotEquals(a, b);
    }

    @Test
    @DisplayName("hash code does not change when the id is set")
    void hashCodeStaysTheSame() {
        Product p = new Product();
        int before = p.hashCode(); // Arrange

        p.setId(UUID.randomUUID()); // Act

        assertEquals(before, p.hashCode()); // Assert
    }

}
