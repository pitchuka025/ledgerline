package com.ledgerline.profile;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_preferences")
public class UserPreferences {
    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Column(name = "login_alerts_enabled", nullable = false)
    private boolean loginAlertsEnabled;

    @Column(name = "email_notifications_enabled", nullable = false)
    private boolean emailNotificationsEnabled;

    @Column(name = "hide_balances", nullable = false)
    private boolean hideBalances;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserPreferences() {
    }

    public UserPreferences(UUID userId, String displayName) {
        this.userId = userId;
        this.displayName = displayName;
        this.loginAlertsEnabled = true;
        this.emailNotificationsEnabled = true;
        this.hideBalances = false;
        this.updatedAt = Instant.now();
    }

    public void updateProfile(String displayName) {
        this.displayName = displayName;
        this.updatedAt = Instant.now();
    }

    public void updateSettings(boolean loginAlertsEnabled, boolean emailNotificationsEnabled, boolean hideBalances) {
        this.loginAlertsEnabled = loginAlertsEnabled;
        this.emailNotificationsEnabled = emailNotificationsEnabled;
        this.hideBalances = hideBalances;
        this.updatedAt = Instant.now();
    }

    public UUID getUserId() { return userId; }
    public String getDisplayName() { return displayName; }
    public boolean isLoginAlertsEnabled() { return loginAlertsEnabled; }
    public boolean isEmailNotificationsEnabled() { return emailNotificationsEnabled; }
    public boolean isHideBalances() { return hideBalances; }
    public Instant getUpdatedAt() { return updatedAt; }
}
