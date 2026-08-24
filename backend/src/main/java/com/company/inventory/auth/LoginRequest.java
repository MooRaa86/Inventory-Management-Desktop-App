package com.company.inventory.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank(message = "Username or email is required")
        String usernameOrEmail,
        @NotBlank(message = "Password is required")
        @Size(max = 128, message = "Password is too long")
        String password) {
}
