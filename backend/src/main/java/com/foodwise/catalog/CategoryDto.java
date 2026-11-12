package com.foodwise.catalog;

import java.util.UUID;

public record CategoryDto(UUID id, String slug, String name, String iconName, String colorHex, int displayOrder) {
}
