package com.foodwise.common;

import com.foodwise.common.enums.Unit;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/**
 * Unit families and conversions. Mass (g, kg) and volume (ml, L, tbsp, tsp) convert within their family;
 * count-like units (pcs, pkt, bunch, pinch) only "convert" to themselves.
 */
public final class Units {

    /** Units allowed on inventory batches, shopping items, purchases and waste logs (schema CHECK constraint). */
    public static final Set<Unit> INVENTORY_UNITS = EnumSet.of(Unit.G, Unit.KG, Unit.ML, Unit.L, Unit.PCS, Unit.PKT, Unit.BUNCH);

    private Units() {
    }

    public enum Family { MASS, VOLUME, PCS, PKT, BUNCH, PINCH }

    public static Family family(Unit unit) {
        return switch (unit) {
            case G, KG -> Family.MASS;
            case ML, L, TBSP, TSP -> Family.VOLUME;
            case PCS -> Family.PCS;
            case PKT -> Family.PKT;
            case BUNCH -> Family.BUNCH;
            case PINCH -> Family.PINCH;
        };
    }

    /** Factor to the family's base unit (g for mass, ml for volume, 1 otherwise). */
    private static BigDecimal toBase(Unit unit) {
        return switch (unit) {
            case KG, L -> BigDecimal.valueOf(1000);
            case TBSP -> BigDecimal.valueOf(15);
            case TSP -> BigDecimal.valueOf(5);
            default -> BigDecimal.ONE;
        };
    }

    public static boolean compatible(Unit a, Unit b) {
        return family(a) == family(b);
    }

    /** Converts a quantity between compatible units; empty when the units are in different families. */
    public static Optional<BigDecimal> convert(BigDecimal quantity, Unit from, Unit to) {
        if (from == to) {
            return Optional.of(quantity);
        }
        if (!compatible(from, to)) {
            return Optional.empty();
        }
        BigDecimal base = quantity.multiply(toBase(from));
        return Optional.of(base.divide(toBase(to), 4, RoundingMode.HALF_UP).stripTrailingZeros());
    }

    public static boolean isInventoryUnit(Unit unit) {
        return unit != null && INVENTORY_UNITS.contains(unit);
    }

    /** Rounds to the 2 decimal places used by NUMERIC(10,2) columns. */
    public static BigDecimal money(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal qty(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP);
    }
}
