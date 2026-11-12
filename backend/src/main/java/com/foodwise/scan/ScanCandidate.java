package com.foodwise.scan;

import com.foodwise.common.enums.StorageLocation;
import com.foodwise.common.enums.Unit;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One recognised item awaiting human review (stored in scan_sessions.parsed_items). Nothing reaches inventory until
 * the user confirms (FR-SCAN-004). {@code expiryDate} is pre-filled only from validated catalog guidance.
 */
public record ScanCandidate(
        String itemName,
        String rawText,
        UUID catalogItemId,
        UUID categoryId,
        BigDecimal quantity,
        Unit unit,
        StorageLocation suggestedLocation,
        String expiryDate,
        boolean expiryEstimated,
        BigDecimal priceInr,
        double confidenceScore,
        boolean lowConfidence,
        boolean experimental) {
}
