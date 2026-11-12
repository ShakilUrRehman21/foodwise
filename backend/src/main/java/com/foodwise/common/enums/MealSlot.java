package com.foodwise.common.enums;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum MealSlot implements DbEnum {
    BREAKFAST("breakfast"),
    LUNCH("lunch"),
    DINNER("dinner"),
    SNACK("snack");

    private final String dbValue;

    MealSlot(String dbValue) {
        this.dbValue = dbValue;
    }

    @Override
    public String dbValue() {
        return dbValue;
    }

    @JsonCreator
    public static MealSlot fromValue(String value) {
        return DbEnum.fromDb(MealSlot.class, value);
    }

    @jakarta.persistence.Converter(autoApply = true)
    public static class Converter extends DbEnumConverter<MealSlot> {
        public Converter() {
            super(MealSlot.class);
        }
    }
}
