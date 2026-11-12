package com.ledgerline.auth;

import com.ledgerline.user.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public final class AuthDtos {
    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank @Email @Size(max = 320) String email,
            @NotBlank @Size(min = 12, max = 72) String password) {
    }

    public record LoginRequest(
            @NotBlank @Email @Size(max = 320) String email,
            @NotBlank @Size(max = 72) String password) {
    }

    public record AuthResponse(String token, UUID userId, String email, UserRole role) {
    }
}
