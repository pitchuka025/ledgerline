package com.ledgerline.profile;

import com.ledgerline.user.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public final class ProfileDtos {
    private ProfileDtos() {
    }

    public record ProfileResponse(UUID id, String email, String displayName, UserRole role, Instant createdAt) {
    }

    public record UpdateProfileRequest(
            @NotBlank @Email @Size(max = 320) String email,
            @NotBlank @Size(max = 100) String displayName) {
    }

    public record PreferencesResponse(String displayName, boolean loginAlertsEnabled,
            boolean emailNotificationsEnabled, boolean hideBalances) {
    }

    public record UpdatePreferencesRequest(boolean loginAlertsEnabled, boolean emailNotificationsEnabled,
            boolean hideBalances) {
    }

    public record ChangePasswordRequest(
            @NotBlank @Size(max = 72) String currentPassword,
            @NotBlank @Size(min = 12, max = 72) String newPassword) {
    }
}
