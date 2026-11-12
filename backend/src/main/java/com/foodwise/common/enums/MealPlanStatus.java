package com.foodwise.common.enums;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum MealPlanStatus implements DbEnum {
    PLANNED("planned"),
    COOKED("cooked"),
    SKIPPED("skipped");

    private final String dbValue;

    MealPlanStatus(String dbValue) {
        this.dbValue = dbValue;
    }

    @Override
    public String dbValue() {
        return dbValue;
    }

    @JsonCreator
    public static MealPlanStatus fromValue(String value) {
        return DbEnum.fromDb(MealPlanStatus.class, value);
    }

    @jakarta.persistence.Converter(autoApply = true)
    public static class Converter extends DbEnumConverter<MealPlanStatus> {
        public Converter() {
            super(MealPlanStatus.class);
        }
    }
}
