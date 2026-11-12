package com.ledgerline.profile;

import com.ledgerline.audit.AuditEvent;
import com.ledgerline.audit.AuditEventRepository;
import com.ledgerline.common.ApiExceptions;
import com.ledgerline.user.AppUser;
import com.ledgerline.user.AppUserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

@Service
public class ProfileService {
    private final AppUserRepository users;
    private final UserPreferencesRepository preferences;
    private final PasswordEncoder passwordEncoder;
    private final AuditEventRepository auditEvents;

    public ProfileService(AppUserRepository users, UserPreferencesRepository preferences,
            PasswordEncoder passwordEncoder, AuditEventRepository auditEvents) {
        this.users = users;
        this.preferences = preferences;
        this.passwordEncoder = passwordEncoder;
        this.auditEvents = auditEvents;
    }

    @Transactional
    public ProfileDtos.ProfileResponse profile(AppUser user) {
        UserPreferences settings = settingsFor(user);
        return new ProfileDtos.ProfileResponse(user.getId(), user.getEmail(), settings.getDisplayName(),
                user.getRole(), user.getCreatedAt());
    }

    @Transactional
    public ProfileDtos.ProfileResponse updateProfile(AppUser user, ProfileDtos.UpdateProfileRequest request) {
        AppUser managedUser = managedUser(user);
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (!managedUser.getEmail().equalsIgnoreCase(email) && users.existsByEmailIgnoreCase(email)) {
            throw new ApiExceptions.Conflict("An account with this email already exists");
        }
        managedUser.updateEmail(email);
        settingsFor(managedUser).updateProfile(request.displayName().trim());
        auditEvents.save(new AuditEvent(managedUser, "PROFILE_UPDATED", "USER", managedUser.getId().toString(),
                "Profile details updated"));
        return profile(managedUser);
    }

    @Transactional
    public ProfileDtos.PreferencesResponse preferences(AppUser user) {
        return toResponse(settingsFor(user));
    }

    @Transactional
    public ProfileDtos.PreferencesResponse updatePreferences(AppUser user,
            ProfileDtos.UpdatePreferencesRequest request) {
        UserPreferences settings = settingsFor(user);
        settings.updateSettings(request.loginAlertsEnabled(), request.emailNotificationsEnabled(),
                request.hideBalances());
        auditEvents.save(new AuditEvent(user, "PREFERENCES_UPDATED", "USER", user.getId().toString(),
                "Account preferences updated"));
        return toResponse(settings);
    }

    @Transactional
    public void changePassword(AppUser user, ProfileDtos.ChangePasswordRequest request) {
        AppUser managedUser = managedUser(user);
        if (!passwordEncoder.matches(request.currentPassword(), managedUser.getPasswordHash())) {
            throw new ApiExceptions.Forbidden("Current password is incorrect");
        }
        if (passwordEncoder.matches(request.newPassword(), managedUser.getPasswordHash())) {
            throw new IllegalArgumentException("New password must be different from the current password");
        }
        managedUser.updatePasswordHash(passwordEncoder.encode(request.newPassword()));
        auditEvents.save(new AuditEvent(managedUser, "PASSWORD_CHANGED", "USER", managedUser.getId().toString(),
                "Password changed by account owner"));
    }

    private AppUser managedUser(AppUser user) {
        return users.findById(user.getId()).orElseThrow(() -> new ApiExceptions.NotFound("User not found"));
    }

    private UserPreferences settingsFor(AppUser user) {
        UUID userId = user.getId();
        return preferences.findById(userId).orElseGet(() -> preferences.save(
                new UserPreferences(userId, defaultDisplayName(user.getEmail()))));
    }

    private String defaultDisplayName(String email) {
        String normalized = email == null ? "" : email.trim();
        if (normalized.isEmpty()) {
            return "User";
        }
        int atIndex = normalized.indexOf('@');
        String localPart = atIndex > 0 ? normalized.substring(0, atIndex) : normalized;
        if (localPart.isEmpty()) {
            return "User";
        }
        return localPart.replaceAll("[._-]+", " ");
    }

    private ProfileDtos.PreferencesResponse toResponse(UserPreferences settings) {
        return new ProfileDtos.PreferencesResponse(settings.getDisplayName(), settings.isLoginAlertsEnabled(),
                settings.isEmailNotificationsEnabled(), settings.isHideBalances());
    }
}
