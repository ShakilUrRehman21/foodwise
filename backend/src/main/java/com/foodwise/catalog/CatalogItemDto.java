package com.foodwise.catalog;

import com.foodwise.common.enums.StorageLocation;
import com.foodwise.common.enums.Unit;

import java.util.UUID;

/**
 * Catalog entry as exposed to clients. Shelf-life fields are null unless validated guidance has been approved
 * (PRD FR-EXP-001); clients must then ask the user for an expiry date.
 */
public record CatalogItemDto(UUID id, String nameEn, String nameHi, UUID categoryId, String categorySlug, String categoryName,
                             Unit defaultUnit, StorageLocation defaultLocation, Integer shelfLifeRoomTempDays,
                             Integer shelfLifeFridgeDays, Integer shelfLifeFreezerDays, boolean perishable,
                             String storageTip) {

    public Integer shelfLifeFor(StorageLocation location) {
        if (location == null) {
            return null;
        }
        return switch (location) {
            case FRIDGE -> shelfLifeFridgeDays;
            case FREEZER -> shelfLifeFreezerDays;
            case PANTRY -> shelfLifeRoomTempDays;
        };
    }

    public String displayName() {
        return nameHi == null || nameHi.equalsIgnoreCase(nameEn) ? nameEn : nameEn + " (" + nameHi + ")";
    }
}
