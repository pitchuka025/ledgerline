CREATE TABLE user_preferences (
    user_id UUID PRIMARY KEY REFERENCES app_users(id) ON DELETE CASCADE,
    display_name VARCHAR(100) NOT NULL,
    login_alerts_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    email_notifications_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    hide_balances BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
