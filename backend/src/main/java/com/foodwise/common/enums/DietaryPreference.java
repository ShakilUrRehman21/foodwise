package com.foodwise.common.enums;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum DietaryPreference implements DbEnum {
    VEGETARIAN("vegetarian"),
    VEGAN("vegan"),
    EGGETARIAN("eggetarian"),
    JAIN("jain"),
    OMNIVORE("omnivore");

    private final String dbValue;

    DietaryPreference(String dbValue) {
        this.dbValue = dbValue;
    }

    @Override
    public String dbValue() {
        return dbValue;
    }

    @JsonCreator
    public static DietaryPreference fromValue(String value) {
        return DbEnum.fromDb(DietaryPreference.class, value);
    }

    @jakarta.persistence.Converter(autoApply = true)
    public static class Converter extends DbEnumConverter<DietaryPreference> {
        public Converter() {
            super(DietaryPreference.class);
        }
    }
}
