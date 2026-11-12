package com.ledgerline.admin;

import com.ledgerline.user.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public final class AdminUserDtos {
    private AdminUserDtos() {
    }

    public record UserResponse(UUID id, String email, String displayName, UserRole role, Instant createdAt) {
    }

    public record CreateUserRequest(
            @NotBlank @Email @Size(max = 320) String email,
            @NotBlank @Size(max = 100) String displayName,
            @NotNull UserRole role,
            @NotBlank @Size(min = 12, max = 72) String password) {
    }

    public record UpdateRoleRequest(@NotNull UserRole role) {
    }
}
