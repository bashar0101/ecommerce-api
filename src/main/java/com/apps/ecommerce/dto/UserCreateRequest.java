package com.apps.ecommerce.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Deliberately has no "role" and no "enabled". Both are decided by the server in
 * AuthService.register. This endpoint is permitAll, so anything accepted here is
 * chosen by an anonymous caller — a role field would let anyone mint an admin.
 */
public record UserCreateRequest(

        @NotBlank(message = "First name is required") //
        @Size(min = 2, max = 50, message = "First name must be between 2 and 50 characters") //
        @Pattern(regexp = "^[\\p{L} '-]+$", message = "First name may only contain letters, spaces, apostrophes and hyphens") //
        String firstName,

        @NotBlank(message = "Last name is required") //
        @Size(min = 2, max = 50, message = "Last name must be between 2 and 50 characters") //
        @Pattern(regexp = "^[\\p{L} '-]+$", message = "Last name may only contain letters, spaces, apostrophes and hyphens") //
        String lastName,

        @NotBlank(message = "Email is required") //
        @Email(message = "Please provide a valid email address") //
        String email,

        // @NotBlank alone would accept "   +   " — it only rejects strings that are
        // entirely whitespace. \S+ forbids whitespace anywhere, the lookaheads
        // require each character class, and 72 is BCrypt's limit: it silently
        // ignores everything past 72 bytes, so a longer password is a lie.
        @NotBlank(message = "Password is required") //
        @Size(min = 8, max = 72, message = "Password must be between 8 and 72 characters") //
        @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)\\S+$", message = "Password needs an uppercase letter, a lowercase letter and a digit, and no spaces") //
        String password) {

}
