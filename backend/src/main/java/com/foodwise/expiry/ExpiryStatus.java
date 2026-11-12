package com.foodwise.expiry;

import com.fasterxml.jackson.annotation.JsonValue;

/** Derived (never persisted) expiry state of an active batch (PRD FR-EXP-002). */
public enum ExpiryStatus {
    FRESH("fresh"),
    USE_SOON("use_soon"),
    EXPIRING_TODAY("expiring_today"),
    EXPIRED("expired");

    private final String value;

    ExpiryStatus(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    /** Recipe scoring weight (Roadmap 5.B3): 3.0 today, 2.0 use-soon, 1.0 otherwise. */
    public double rescueMultiplier() {
        return switch (this) {
            case EXPIRING_TODAY -> 3.0;
            case USE_SOON -> 2.0;
            default -> 1.0;
        };
    }

    public boolean isUrgent() {
        return this == USE_SOON || this == EXPIRING_TODAY;
    }
}
