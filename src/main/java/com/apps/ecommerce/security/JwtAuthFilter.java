package com.apps.ecommerce.security;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsChecker;
import org.springframework.security.authentication.AccountStatusUserDetailsChecker;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.apps.ecommerce.entity.User;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
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

                // instanceof, not a cast. A plain cast threw ClassCastException for
                // every request while the service still returned Spring's own
                // UserDetails, and the catch below hid it - so a valid token always
                // ended up anonymous and every protected endpoint answered 401.
                if (user instanceof AppUserDetails details) {
                    LocalDateTime changedAt = details.getUser().getCredentialsChangedAt();
                    Instant issuedAt = claims.getIssuedAt().toInstant();
                    if (changedAt != null
                            && issuedAt.isBefore(changedAt.atZone(ZoneId.systemDefault()).toInstant())) {
                        // The token predates the password change, so it is revoked.
                        filterChain.doFilter(request, response);
                        return;
                    }
                }
                // The AuthenticationManager runs this on the login path, but nothing
                // did on the token path — so an account disabled after login kept
                // working for the rest of its 24h token. Throws for disabled, locked
                // and expired accounts; the catch below turns that into "anonymous".
                accountStatusChecker.check(user);

                var auth = new UsernamePasswordAuthenticationToken(
                        user, null, user.getAuthorities());
                SecurityContextHolder.getContext().setAuthentication(auth);

            } catch (Exception ex) {
                // Bad token: stay anonymous and let the URL rules answer 401. Logged
                // at debug because an expired token is normal traffic - but it must
                // be logged, or a real bug in here is invisible, which is exactly
                // what happened with the ClassCastException above.
                log.debug("Rejected JWT: {}", ex.toString());
            }
        }

        filterChain.doFilter(request, response);
    }

}
