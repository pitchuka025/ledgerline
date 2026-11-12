package com.ledgerline.profile;

import com.ledgerline.admin.AdminUserDtos;
import com.ledgerline.admin.AdminUserService;
import com.ledgerline.audit.AuditEventRepository;
import com.ledgerline.common.ApiExceptions;
import com.ledgerline.user.AppUser;
import com.ledgerline.user.AppUserRepository;
import com.ledgerline.user.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProfileAndAdminServiceTest {
    @Test
    void createsDefaultPreferencesAndUpdatesAccountPreferences() {
        AppUser user = new AppUser("rupas.sharma@example.test", "hash", UserRole.CUSTOMER);
        AppUserRepository users = mock(AppUserRepository.class);
        UserPreferencesRepository preferences = mock(UserPreferencesRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        AuditEventRepository audit = mock(AuditEventRepository.class);
        when(preferences.findById(user.getId())).thenReturn(Optional.empty());
        when(preferences.save(any(UserPreferences.class))).thenAnswer(invocation -> invocation.getArgument(0));
        ProfileService service = new ProfileService(users, preferences, encoder, audit);

        assertThat(service.profile(user).displayName()).isEqualTo("rupas sharma");
        ProfileDtos.PreferencesResponse updated = service.updatePreferences(user,
                new ProfileDtos.UpdatePreferencesRequest(false, true, true));

        assertThat(updated.loginAlertsEnabled()).isFalse();
        assertThat(updated.emailNotificationsEnabled()).isTrue();
        assertThat(updated.hideBalances()).isTrue();
        verify(audit).save(any());
    }

    @Test
    void passwordChangeRequiresTheCurrentPasswordAndStoresOnlyAnEncodedValue() {
        AppUser user = new AppUser("owner@example.test", "current-hash", UserRole.CUSTOMER);
        AppUserRepository users = mock(AppUserRepository.class);
        UserPreferencesRepository preferences = mock(UserPreferencesRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        AuditEventRepository audit = mock(AuditEventRepository.class);
        when(users.findById(user.getId())).thenReturn(Optional.of(user));
        when(encoder.matches("correct-current", "current-hash")).thenReturn(true);
        when(encoder.matches("new-private-password", "current-hash")).thenReturn(false);
        when(encoder.encode("new-private-password")).thenReturn("new-hash");
        ProfileService service = new ProfileService(users, preferences, encoder, audit);

        service.changePassword(user, new ProfileDtos.ChangePasswordRequest("correct-current", "new-private-password"));

        assertThat(user.getPasswordHash()).isEqualTo("new-hash");
        verify(audit).save(any());
        assertThatThrownBy(() -> service.changePassword(user,
                new ProfileDtos.ChangePasswordRequest("incorrect", "another-private-password")))
                .isInstanceOf(ApiExceptions.Forbidden.class);
    }

    @Test
    void administratorsCanCreateOnlyCustomerOrAuditorUsers() {
        AppUser admin = new AppUser("admin@example.test", "hash", UserRole.ADMIN);
        AppUserRepository users = mock(AppUserRepository.class);
        UserPreferencesRepository preferences = mock(UserPreferencesRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        AuditEventRepository audit = mock(AuditEventRepository.class);
        when(users.existsByEmailIgnoreCase("new@example.test")).thenReturn(false);
        when(users.save(any(AppUser.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(encoder.encode("temporary-private-password")).thenReturn("encoded-hash");
        when(preferences.save(any(UserPreferences.class))).thenAnswer(invocation -> invocation.getArgument(0));
        AdminUserService service = new AdminUserService(users, preferences, encoder, audit);

        AdminUserDtos.UserResponse created = service.create(admin, new AdminUserDtos.CreateUserRequest(
                " New@Example.Test ", "New User", UserRole.AUDITOR, "temporary-private-password"));

        assertThat(created.email()).isEqualTo("new@example.test");
        assertThat(created.displayName()).isEqualTo("New User");
        assertThat(created.role()).isEqualTo(UserRole.AUDITOR);
        assertThatThrownBy(() -> service.create(admin, new AdminUserDtos.CreateUserRequest(
                "admin2@example.test", "Admin Two", UserRole.ADMIN, "temporary-private-password")))
                .isInstanceOf(ApiExceptions.Forbidden.class);
        verify(audit).save(any());
    }

    @Test
    void administratorsCannotChangeTheirOwnRole() {
        AppUser admin = new AppUser("admin@example.test", "hash", UserRole.ADMIN);
        AdminUserService service = new AdminUserService(mock(AppUserRepository.class),
                mock(UserPreferencesRepository.class), mock(PasswordEncoder.class),
                mock(AuditEventRepository.class));

        assertThatThrownBy(() -> service.updateRole(admin, admin.getId(),
                new AdminUserDtos.UpdateRoleRequest(UserRole.CUSTOMER)))
                .isInstanceOf(ApiExceptions.Forbidden.class);
    }
}
