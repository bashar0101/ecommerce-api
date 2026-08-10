package com.apps.ecommerce.entity;

import java.util.UUID;

import com.apps.ecommerce.enums.Role;

import jakarta.annotation.Generated;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;

import org.springframework.data.domain.Persistable;

import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AllArgsConstructor;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@NoArgsConstructor
@AllArgsConstructor
@Setter
@Getter
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * Spring Data decides between persist() and merge() by asking isNew(), which by
     * default just checks whether the id is null. Since the seeder assigns a fixed
     * id up front, that default would report "not new" and send a brand-new user
     * down the merge() path — which fails with StaleObjectStateException because
     * there is no row to merge into. Tracking newness explicitly fixes that.
     * Lombok's @Getter generates isNew() from this field, satisfying Persistable.
     */
    // @Transient
    // private boolean isNew = true;

    // @PostPersist
    // @PostLoad
    // void markNotNew() {
    // this.isNew = false;
    // }

    /**
     * Deliberately no @GeneratedValue: Hibernate's UUID generator overwrites any id
     * we assign ourselves, so the id is filled in here instead. That lets the
     * seeder
     * pin a known, fixed UUID for the test user while normal users still get a
     * random one. Leaving the id null until persist also keeps save() on the
     * persist() path — an assigned id makes Spring Data treat the entity as
     * detached and call merge(), which fails when the row doesn't exist yet.
     */
    // @PrePersist
    // void assignIdIfMissing() {
    // if (id == null) {
    // id = UUID.randomUUID();
    // }
    // }

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
        return java.util.Objects.hash(id);
    }

}
