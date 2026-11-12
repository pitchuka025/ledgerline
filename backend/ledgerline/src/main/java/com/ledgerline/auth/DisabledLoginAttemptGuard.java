package com.ledgerline.auth;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "ledgerline.security.login-rate-limit", name = "enabled",
        havingValue = "false", matchIfMissing = true)
public class DisabledLoginAttemptGuard implements LoginAttemptGuard {
    @Override
    public void checkAllowed(String email, String clientAddress) {
    }

    @Override
    public void recordFailure(String email, String clientAddress) {
    }

    @Override
    public void clear(String email, String clientAddress) {
    }
}
