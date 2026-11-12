package com.foodwise.common;

import com.foodwise.common.enums.Unit;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class UnitsTest {

    @Test
    void convertsWithinFamilies() {
        assertThat(Units.convert(new BigDecimal("1.5"), Unit.KG, Unit.G)).hasValueSatisfying(v -> assertThat(v).isEqualByComparingTo("1500"));
        assertThat(Units.convert(new BigDecimal("250"), Unit.ML, Unit.L)).hasValueSatisfying(v -> assertThat(v).isEqualByComparingTo("0.25"));
        assertThat(Units.convert(new BigDecimal("2"), Unit.TBSP, Unit.ML)).hasValueSatisfying(v -> assertThat(v).isEqualByComparingTo("30"));
    }

    @Test
    void refusesAcrossFamilies() {
        assertThat(Units.convert(BigDecimal.ONE, Unit.G, Unit.PCS)).isEmpty();
        assertThat(Units.convert(BigDecimal.ONE, Unit.BUNCH, Unit.PKT)).isEmpty();
        assertThat(Units.isInventoryUnit(Unit.TSP)).isFalse();
        assertThat(Units.isInventoryUnit(Unit.L)).isTrue();
    }
}
