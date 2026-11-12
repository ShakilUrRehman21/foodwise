package com.foodwise.expiry;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class ExpiryClassifierTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 12);

    @ParameterizedTest(name = "{0} days -> {1}")
    @CsvSource({"-5,EXPIRED", "-1,EXPIRED", "0,EXPIRING_TODAY", "1,USE_SOON", "2,USE_SOON", "3,USE_SOON", "4,FRESH", "30,FRESH"})
    void classifiesOnDatePrecision(int days, ExpiryStatus expected) {
        assertThat(ExpiryClassifier.classify(TODAY.plusDays(days), TODAY)).isEqualTo(expected);
        assertThat(ExpiryClassifier.daysUntil(TODAY.plusDays(days), TODAY)).isEqualTo(days);
    }

    @ParameterizedTest
    @CsvSource({"EXPIRING_TODAY,3.0", "USE_SOON,2.0", "FRESH,1.0", "EXPIRED,1.0"})
    void rescueMultipliers(ExpiryStatus status, double multiplier) {
        assertThat(status.rescueMultiplier()).isEqualTo(multiplier);
    }
}
