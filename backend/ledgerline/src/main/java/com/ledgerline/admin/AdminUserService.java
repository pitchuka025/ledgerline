package com.ledgerline.admin;

import com.ledgerline.audit.AuditEvent;
import com.ledgerline.audit.AuditEventRepository;
import com.ledgerline.common.ApiExceptions;
import com.ledgerline.profile.UserPreferences;
import com.ledgerline.profile.UserPreferencesRepository;
import com.ledgerline.user.AppUser;
import com.ledgerline.user.AppUserRepository;
import com.ledgerline.user.UserRole;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class AdminUserService {
    private final AppUserRepository users;
    private final UserPreferencesRepository preferences;
    private final PasswordEncoder passwordEncoder;
    private final AuditEventRepository auditEvents;

    public AdminUserService(AppUserRepository users, UserPreferencesRepository preferences,
            PasswordEncoder passwordEncoder, AuditEventRepository auditEvents) {
        this.users = users;
        this.preferences = preferences;
        this.passwordEncoder = passwordEncoder;
        this.auditEvents = auditEvents;
    }

    @Transactional(readOnly = true)
    public List<AdminUserDtos.UserResponse> list() {
        return users.findAll().stream()
                .sorted(Comparator.comparing(AppUser::getCreatedAt).reversed())
                .map(this::response)
                .toList();
    }

    @Transactional
    public AdminUserDtos.UserResponse create(AppUser actor, AdminUserDtos.CreateUserRequest request) {
        if (request.role() == UserRole.ADMIN) {
            throw new ApiExceptions.Forbidden("Administrator roles can only be provisioned outside this user form");
        }
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (users.existsByEmailIgnoreCase(email)) {
            throw new ApiExceptions.Conflict("An account with this email already exists");
        }
        AppUser created = users.save(new AppUser(email, passwordEncoder.encode(request.password()), request.role()));
        preferences.save(new UserPreferences(created.getId(), request.displayName().trim()));
        auditEvents.save(new AuditEvent(actor, "USER_CREATED", "USER", created.getId().toString(),
                "Created " + request.role().name() + " user " + email));
        return new AdminUserDtos.UserResponse(created.getId(), created.getEmail(), request.displayName().trim(),
                created.getRole(), created.getCreatedAt());
    }

    @Transactional
    public AdminUserDtos.UserResponse updateRole(AppUser actor, UUID userId,
            AdminUserDtos.UpdateRoleRequest request) {
        if (userId.equals(actor.getId())) {
            throw new ApiExceptions.Forbidden("Administrators cannot change their own role");
        }
        if (request.role() == UserRole.ADMIN) {
            throw new ApiExceptions.Forbidden("Administrator roles cannot be assigned from this endpoint");
        }
        AppUser target = users.findById(userId)
                .orElseThrow(() -> new ApiExceptions.NotFound("User not found"));
        if (target.getRole() == UserRole.ADMIN) {
            throw new ApiExceptions.Forbidden("Administrator roles cannot be changed from this endpoint");
        }
        target.updateRole(request.role());
        auditEvents.save(new AuditEvent(actor, "USER_ROLE_UPDATED", "USER", target.getId().toString(),
                "Role changed to " + request.role().name()));
        return response(target);
    }

    private AdminUserDtos.UserResponse response(AppUser user) {
        String defaultName = defaultDisplayName(user.getEmail());
        String displayName = preferences.findById(user.getId()).map(UserPreferences::getDisplayName)
                .orElse(defaultName);
        return new AdminUserDtos.UserResponse(user.getId(), user.getEmail(), displayName, user.getRole(),
                user.getCreatedAt());
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
}
