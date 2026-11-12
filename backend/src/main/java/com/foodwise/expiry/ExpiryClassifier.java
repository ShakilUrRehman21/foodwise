package com.foodwise.expiry;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Pure, deterministic expiry classification on DATE precision (Roadmap 3.B1).
 * <ul>
 *   <li>{@code expiry < today}  -> EXPIRED</li>
 *   <li>{@code expiry == today} -> EXPIRING_TODAY (never EXPIRED on its last day)</li>
 *   <li>1-3 days left           -> USE_SOON (the spec's "2-3 days" band, extended to cover "tomorrow")</li>
 *   <li>more than 3 days        -> FRESH</li>
 * </ul>
 */
public final class ExpiryClassifier {

    public static final int USE_SOON_MAX_DAYS = 3;

    private ExpiryClassifier() {
    }

    public static ExpiryStatus classify(LocalDate expiryDate, LocalDate today) {
        long days = daysUntil(expiryDate, today);
        if (days < 0) {
            return ExpiryStatus.EXPIRED;
        }
        if (days == 0) {
            return ExpiryStatus.EXPIRING_TODAY;
        }
        if (days <= USE_SOON_MAX_DAYS) {
            return ExpiryStatus.USE_SOON;
        }
        return ExpiryStatus.FRESH;
    }

    /** Signed whole days from today to the expiry date (negative when overdue). */
    public static long daysUntil(LocalDate expiryDate, LocalDate today) {
        return ChronoUnit.DAYS.between(today, expiryDate);
    }
}
