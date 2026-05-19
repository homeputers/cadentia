package com.cadentia.songimport;

public record RateLimitPolicy(
        RateLimitBehavior behavior,
        Integer requestsPerMinute,
        boolean retryAfterHeaderHonored) {

    public RateLimitPolicy {
        behavior = ImportConnectorValidation.requireNonNull(behavior, "behavior");
        if (requestsPerMinute != null && requestsPerMinute <= 0) {
            throw new IllegalArgumentException("requestsPerMinute must be positive when provided");
        }
    }

    public static RateLimitPolicy notApplicable() {
        return new RateLimitPolicy(RateLimitBehavior.NOT_APPLICABLE, null, false);
    }
}
