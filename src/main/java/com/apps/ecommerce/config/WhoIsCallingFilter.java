package com.apps.ecommerce.config;

import java.io.IOException;

import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class WhoIsCallingFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        log.info("{} {} ip={} ua={}", req.getMethod(), req.getRequestURI(),
                req.getHeader("X-Forwarded-For"), req.getHeader("User-Agent"));
        chain.doFilter(req, res);
    }
}
