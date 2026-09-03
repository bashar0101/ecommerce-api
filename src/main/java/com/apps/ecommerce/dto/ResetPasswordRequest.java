package com.apps.ecommerce.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(

        @NotBlank(message = "Token is required") String token,

        // Same rules as registration. A reset must not be a way to set a weaker
        // password than the sign-up form would have accepted.
        @NotBlank(message = "Password is required") //
        @Size(min = 8, max = 72, message = "Password must be between 8 and 72 characters") //
        @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)\\S+$", message = "Password needs an uppercase letter, a lowercase letter and a digit, and no spaces") //
        String newPassword) {
}
