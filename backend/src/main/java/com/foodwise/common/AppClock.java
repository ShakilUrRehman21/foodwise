package com.foodwise.common;

import com.foodwise.config.FoodwiseProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Single source of "now" in the kitchen's time zone (default Asia/Kolkata).
 * Expiry is date-precision (SQL DATE), so "today" must be computed in the user's zone, not the server's or the DB's.
 */
@Component
public class AppClock {

    private final Clock clock;

    @Autowired
    public AppClock(FoodwiseProperties properties) {
        this(Clock.system(ZoneId.of(properties.timezone())));
    }

    public AppClock(Clock clock) {
        this.clock = clock;
    }

    public LocalDate today() {
        return LocalDate.now(clock);
    }

    public LocalTime timeOfDay() {
        return LocalTime.now(clock);
    }

    public Instant now() {
        return clock.instant();
    }

    public ZonedDateTime zonedNow() {
        return ZonedDateTime.now(clock);
    }

    public ZoneId zone() {
        return clock.getZone();
    }
}
