package com.apps.ecommerce.entity;

import java.util.UUID;

import com.apps.ecommerce.enums.Role;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import lombok.AllArgsConstructor;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@NoArgsConstructor
@AllArgsConstructor
@Setter
@Getter
public class User {

    @Id
    private UUID id;

    /**
     * Hibernate's UUID generator overwrites any id we assign ourselves, so the id
     * is filled in here instead. That lets the seeder pin a known, fixed UUID for
     * the test user while normal users still get a random one.
     */
    @PrePersist
    void assignIdIfMissing() {
        if (id == null) {
            id = UUID.randomUUID();
        }
    }

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private Role role = Role.USER;

    @Column(nullable = false)
    private boolean enabled;

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof User other))
            return false;
        return id != null && id.equals(other.getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode(); // fixed value, never changes when id is set
    }

}
