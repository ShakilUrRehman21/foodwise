package com.foodwise.inventory;

import com.foodwise.common.enums.BatchStatus;
import com.foodwise.common.enums.LeftoverType;
import com.foodwise.common.enums.StorageLocation;
import com.foodwise.common.enums.Unit;
import com.foodwise.expiry.ExpiryStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Inventory batch with derived expiry state. {@code expiryEstimated} drives the "Estimated" badge. */
public record BatchDto(UUID id, UUID catalogItemId, String name, String nameHi, UUID categoryId, String categorySlug,
                       String categoryName, StorageLocation storageLocation, BigDecimal initialQuantity,
                       BigDecimal currentQuantity, Unit unit, LocalDate purchaseDate, LocalDate expiryDate,
                       boolean expiryEstimated, ExpiryStatus expiryStatus, long daysUntilExpiry, BigDecimal costInr,
                       boolean leftover, LeftoverType leftoverType, Instant cookedAt, String leftoverNotes,
                       BatchStatus status, Instant createdAt, Instant updatedAt) {
}
