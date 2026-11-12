package com.foodwise.catalog;

import com.foodwise.common.enums.StorageLocation;
import com.foodwise.common.enums.Unit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CatalogMatcherTest {

    private static CatalogItemDto item(String en, String hi, Unit unit) {
        return new CatalogItemDto(UUID.nameUUIDFromBytes(en.getBytes()), en, hi, UUID.randomUUID(), "x", "x", unit,
                StorageLocation.FRIDGE, null, null, null, true, null);
    }

    private final CatalogMatcher matcher;

    CatalogMatcherTest() {
        CatalogService catalog = mock(CatalogService.class);
        when(catalog.allItems()).thenReturn(List.of(
                item("Milk", "Doodh", Unit.ML), item("Fresh Cream", "Malai", Unit.ML), item("Toor Dal", "Arhar Dal", Unit.KG),
                item("Tomato", "Tamatar", Unit.G), item("Rice", "Chawal", Unit.KG), item("Basmati Rice", "Basmati Chawal", Unit.KG),
                item("Whole Wheat Flour", "Atta", Unit.KG), item("Okra", "Bhindi", Unit.G), item("Paneer", "Paneer", Unit.G),
                item("Coriander Leaves", "Dhaniya", Unit.BUNCH), item("Curd", "Dahi", Unit.G), item("Spinach", "Palak", Unit.BUNCH)));
        matcher = new CatalogMatcher(catalog);
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "Amul Taaza Toned Milk,Milk", "Amul Gold Full Cream Milk,Milk", "Amul Fresh Cream,Fresh Cream",
            "Tata Sampann Toor Dal,Toor Dal", "Fresho Tomatoes Hybrid,Tomato", "India Gate Basmati Rice,Basmati Rice",
            "Aashirvaad Shudh Chakki Atta,Whole Wheat Flour", "Lady Finger,Okra", "Dhania,Coriander Leaves",
            "Mother Dairy Dahi,Curd", "Spinch,Spinach"})
    void matchesReceiptWording(String text, String expected) {
        assertThat(matcher.bestMatch(text)).hasValueSatisfying(m -> {
            assertThat(m.item().nameEn()).isEqualTo(expected);
            assertThat(m.score()).isGreaterThanOrEqualTo(0.75);
        });
    }

    @Test
    void garbageDoesNotConfidentlyMatch() {
        assertThat(matcher.bestMatch("XQZ PLT 44").map(CatalogMatcher.Match::score).orElse(0.0)).isLessThan(0.75);
    }
}
