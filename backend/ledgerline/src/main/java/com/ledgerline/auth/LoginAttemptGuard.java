package com.ledgerline.auth;

public interface LoginAttemptGuard {
    void checkAllowed(String email, String clientAddress);

    void recordFailure(String email, String clientAddress);

    void clear(String email, String clientAddress);
}
