package com.apps.ecommerce.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The API answered 200 to a preflight but sent no Access-Control-Allow-Origin
 * header, so every call from the front end died in the browser while curl and
 * every other test still passed - curl does not enforce CORS, browsers do.
 *
 * These tests assert on the headers themselves, which is the only thing the
 * browser actually looks at.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class CorsConfigIntegrationTest {

    /** The default in application.properties. */
    private static final String ALLOWED = "http://localhost:3000";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JavaMailSender mailSender;

    @Test
    @DisplayName("preflight from the front end is allowed, and needs no token")
    void preflightIsAllowedWithoutAuthentication() throws Exception {
        // /orders is authenticated, but a preflight never carries the
        // Authorization header. If the CORS filter did not run before the auth
        // rules this would be 401 and the real request would never be sent.
        mockMvc.perform(options("/api/v1/orders")
                .header("Origin", ALLOWED)
                .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", ALLOWED));
    }

    @Test
    @DisplayName("the Authorization header is allowed through the preflight")
    void preflightAllowsTheAuthorizationHeader() throws Exception {
        // Every authenticated call sends Authorization. A header missing from
        // allowedHeaders fails the preflight and the request is never made.
        mockMvc.perform(options("/api/v1/orders")
                .header("Origin", ALLOWED)
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "Authorization, Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().stringValues("Access-Control-Allow-Headers",
                        "Authorization, Content-Type"));
    }

    @Test
    @DisplayName("a real request carries the header back, so the browser keeps the response")
    void actualRequestGetsTheHeader() throws Exception {
        // The preflight passing is not enough: without the header on the real
        // response the browser throws the body away.
        mockMvc.perform(get("/api/v1/products").header("Origin", ALLOWED))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", ALLOWED));
    }

    @Test
    @DisplayName("an origin that is not listed is refused")
    void unknownOriginIsRejected() throws Exception {
        mockMvc.perform(options("/api/v1/orders")
                .header("Origin", "https://evil.example.com")
                .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden());
    }
}
