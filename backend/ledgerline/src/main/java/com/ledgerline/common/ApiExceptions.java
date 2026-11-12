package com.ledgerline.common;

public final class ApiExceptions {
    private ApiExceptions() {
    }

    public static class NotFound extends RuntimeException {
        public NotFound(String message) { super(message); }
    }

    public static class Forbidden extends RuntimeException {
        public Forbidden(String message) { super(message); }
    }

    public static class Conflict extends RuntimeException {
        public Conflict(String message) { super(message); }
    }

    public static class TooManyRequests extends RuntimeException {
        private final long retryAfterSeconds;

        public TooManyRequests(long retryAfterSeconds) {
            super("Too many failed sign-in attempts. Try again after the rate-limit window.");
            this.retryAfterSeconds = retryAfterSeconds;
        }

        public long getRetryAfterSeconds() { return retryAfterSeconds; }
    }
}
