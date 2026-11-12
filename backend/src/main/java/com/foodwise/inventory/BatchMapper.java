package com.foodwise.inventory;

import com.foodwise.catalog.CatalogItemDto;
import com.foodwise.catalog.CatalogService;
import com.foodwise.catalog.CategoryDto;
import com.foodwise.common.AppClock;
import com.foodwise.expiry.ExpiryClassifier;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class BatchMapper {

    private final CatalogService catalog;
    private final AppClock clock;

    public BatchMapper(CatalogService catalog, AppClock clock) {
        this.catalog = catalog;
        this.clock = clock;
    }

    public BatchDto toDto(InventoryBatch b) {
        return toDto(b, clock.today());
    }

    public BatchDto toDto(InventoryBatch b, LocalDate today) {
        CategoryDto cat = catalog.category(b.getCategoryId()).orElse(null);
        CatalogItemDto item = catalog.item(b.getCatalogItemId()).orElse(null);
        String nameHi = item != null && item.nameHi() != null && !item.nameHi().equalsIgnoreCase(b.getCustomItemName())
                ? item.nameHi() : null;
        return new BatchDto(b.getId(), b.getCatalogItemId(), b.getCustomItemName(), nameHi, b.getCategoryId(),
                cat == null ? null : cat.slug(), cat == null ? null : cat.name(), b.getStorageLocation(),
                b.getInitialQuantity(), b.getCurrentQuantity(), b.getUnit(), b.getPurchaseDate(), b.getExpiryDate(),
                b.isExpiryEstimated(), ExpiryClassifier.classify(b.getExpiryDate(), today),
                ExpiryClassifier.daysUntil(b.getExpiryDate(), today), b.getCostInr(), b.isLeftover(), b.getLeftoverType(),
                b.getCookedAt(), b.getLeftoverNotes(), b.getStatus(), b.getCreatedAt(), b.getUpdatedAt());
    }
}
