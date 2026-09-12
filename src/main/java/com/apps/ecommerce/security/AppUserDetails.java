package com.apps.ecommerce.security;

import java.util.Collection;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.apps.ecommerce.entity.User;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Wraps our own User entity instead of copying its fields into Spring's
 * org.springframework.security.core.userdetails.User.
 *
 * The reason is JwtAuthFilter: it needs credentialsChangedAt to decide whether a
 * token predates a password change, and that field only exists on the entity.
 * Spring's built-in User has no room for it, so a cast to our entity threw
 * ClassCastException on every request - silently, because the filter caught and
 * ignored it. Carrying the entity makes the data available without a second
 * database round trip.
 */
@RequiredArgsConstructor
public class AppUserDetails implements UserDetails {

    @Getter
    private final User user;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // Spring expects the ROLE_ prefix; hasRole("ADMIN") looks for ROLE_ADMIN.
        return List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
    }

    @Override
    public String getPassword() {
        return user.getPassword();
    }

    @Override
    public String getUsername() {
        return user.getEmail();
    }

    @Override
    public boolean isEnabled() {
        return user.isEnabled();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }
}
