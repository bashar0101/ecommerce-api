package com.apps.ecommerce.security;

import java.io.IOException;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsChecker;
import org.springframework.security.authentication.AccountStatusUserDetailsChecker;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final AppUserDetailsService userDetailsService;
    private final UserDetailsChecker accountStatusChecker = new AccountStatusUserDetailsChecker();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            try {
                Claims claims = jwtService.parse(header.substring(7));
                UserDetails user = userDetailsService.loadUserByUsername(claims.getSubject());

                // The AuthenticationManager runs this on the login path, but nothing
                // did on the token path — so an account disabled after login kept
                // working for the rest of its 24h token. Throws for disabled, locked
                // and expired accounts; the catch below turns that into "anonymous".
                accountStatusChecker.check(user);

                var auth = new UsernamePasswordAuthenticationToken(
                        user, null, user.getAuthorities());
                SecurityContextHolder.getContext().setAuthentication(auth);

            } catch (Exception ignored) {
                // bad token: stay anonymous, the rules below will reject it
            }
        }

        filterChain.doFilter(request, response);
    }

}
