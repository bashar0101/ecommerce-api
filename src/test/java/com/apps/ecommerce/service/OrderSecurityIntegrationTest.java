package com.apps.ecommerce.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class OrderSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("no token means 401")
    void requiresToken() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":[]}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("a normal user cannot delete a product")
    @WithMockUser(roles = "USER")
    void userCannotDeleteProduct() throws Exception {
        mockMvc.perform(delete("/api/v1/products/" + UUID.randomUUID()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("an admin user can delete a product")
    @WithMockUser(roles = "ADMIN")
    void adminCanDeleteProduct() throws Exception {
        mockMvc.perform(delete("/api/v1/products/" + UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }
}
