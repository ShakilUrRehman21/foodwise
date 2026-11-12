package com.foodwise.common.enums;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Enum stored in the database (and exposed in JSON) as a lowercase string, e.g. {@code BatchStatus.ACTIVE <-> "active"}.
 * Database CHECK constraints stay lowercase; Java code uses UPPERCASE constants (SYSTEM_DESIGN 7.2).
 */
public interface DbEnum {

    @JsonValue
    String dbValue();

    static <E extends Enum<E> & DbEnum> E fromDb(Class<E> type, String value) {
        if (value == null) {
            return null;
        }
        for (E e : type.getEnumConstants()) {
            if (e.dbValue().equals(value) || e.dbValue().equalsIgnoreCase(value) || e.name().equalsIgnoreCase(value)) {
                return e;
            }
        }
        throw new IllegalArgumentException("Unknown " + type.getSimpleName() + " value: " + value);
    }
}
