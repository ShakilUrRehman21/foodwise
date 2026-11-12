package com.foodwise.recipe;

import com.foodwise.common.enums.Unit;
import com.foodwise.expiry.ExpiryStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Roadmap 5.B6 scoring scenarios. */
class RecipeMatchingServiceTest {

    private final RecipeMatchingService matcher = new RecipeMatchingService();
    private final UUID paneer = UUID.randomUUID(), spinach = UUID.randomUUID(), potato = UUID.randomUUID(),
            cauliflower = UUID.randomUUID(), onion = UUID.randomUUID(), salt = UUID.randomUUID();

    private static RecipeIngredient ing(UUID id, String name, String qty, Unit unit, boolean optional, boolean staple) {
        RecipeIngredient i = new RecipeIngredient();
        i.setCatalogItemId(id);
        i.setIngredientName(name);
        i.setQuantity(new BigDecimal(qty));
        i.setUnit(unit);
        i.setOptional(optional);
        i.setPantryStaple(staple);
        return i;
    }

    private static RecipeMatchingService.Portion p(String qty, Unit unit, ExpiryStatus status) {
        return new RecipeMatchingService.Portion(new BigDecimal(qty), unit, status);
    }

    private List<RecipeIngredient> palakPaneer() {
        return List.of(ing(paneer, "Paneer", "200", Unit.G, false, false), ing(spinach, "Spinach", "1", Unit.BUNCH, false, false),
                ing(onion, "Onion", "100", Unit.G, true, false), ing(salt, "Salt", "1", Unit.TSP, false, true));
    }

    private List<RecipeIngredient> alooGobi() {
        return List.of(ing(potato, "Potato", "250", Unit.G, false, false), ing(cauliflower, "Cauliflower", "0.5", Unit.PCS, false, false),
                ing(salt, "Salt", "1", Unit.TSP, false, true));
    }

    private Map<UUID, List<RecipeMatchingService.Portion>> stock() {
        Map<UUID, List<RecipeMatchingService.Portion>> s = new HashMap<>();
        s.put(paneer, List.of(p("200", Unit.G, ExpiryStatus.EXPIRING_TODAY)));
        s.put(spinach, List.of(p("1", Unit.BUNCH, ExpiryStatus.FRESH)));
        s.put(potato, List.of(p("1", Unit.KG, ExpiryStatus.FRESH)));
        s.put(cauliflower, List.of(p("1", Unit.PCS, ExpiryStatus.FRESH)));
        return s;
    }

    @Test
    void readyWhenAllRequiredInStockAcrossConvertibleUnits() {
        var palak = matcher.match(palakPaneer(), 1.0, stock());
        var aloo = matcher.match(alooGobi(), 1.0, stock());
        assertThat(palak.readiness()).isEqualTo(RecipeMatchingService.Readiness.READY_TO_COOK);
        assertThat(aloo.readiness()).isEqualTo(RecipeMatchingService.Readiness.READY_TO_COOK); // 1 kg stock covers 250 g
        assertThat(palak.requiredCount()).isEqualTo(2); // staple excluded, optional not required
    }

    @Test
    void expiringIngredientsRankHigher() {
        var palak = matcher.match(palakPaneer(), 1.0, stock());
        var aloo = matcher.match(alooGobi(), 1.0, stock());
        assertThat(palak.rankScore()).isGreaterThan(aloo.rankScore());
        assertThat(RecipeMatchingService.ordering().compare(palak, aloo)).isNegative();
        assertThat(palak.usesExpiring()).containsExactly("Paneer");
    }

    @Test
    void scalingServingsCanMakeStockInsufficient() {
        var doubled = matcher.match(palakPaneer(), 2.0, stock());
        assertThat(doubled.readiness()).isNotEqualTo(RecipeMatchingService.Readiness.READY_TO_COOK);
        assertThat(doubled.missing()).contains("Paneer");
    }

    @Test
    void almostAndNotReady() {
        var oneMissing = matcher.match(palakPaneer(), 1.0, Map.of(paneer, stock().get(paneer)));
        assertThat(oneMissing.readiness()).isEqualTo(RecipeMatchingService.Readiness.ALMOST_COOKABLE);
        assertThat(oneMissing.matchPercent()).isEqualTo(50);
        var empty = matcher.match(palakPaneer(), 1.0, Map.of());
        assertThat(empty.readiness()).isEqualTo(RecipeMatchingService.Readiness.NOT_READY);
        assertThat(empty.matchPercent()).isZero();
    }

    @Test
    void differentUnitFamiliesCountAsPresent() {
        var m = matcher.match(List.of(ing(spinach, "Spinach", "200", Unit.G, false, false)), 1.0, stock());
        assertThat(m.readiness()).isEqualTo(RecipeMatchingService.Readiness.READY_TO_COOK);
        assertThat(m.ingredients().getFirst().unitMismatch()).isTrue();
    }
}
