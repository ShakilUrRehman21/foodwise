package com.foodwise.common.enums;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum RecipeDiet implements DbEnum {
    VEGETARIAN("vegetarian"),
    VEGAN("vegan"),
    EGGETARIAN("eggetarian"),
    NON_VEGETARIAN("non-vegetarian");

    private final String dbValue;

    RecipeDiet(String dbValue) {
        this.dbValue = dbValue;
    }

    @Override
    public String dbValue() {
        return dbValue;
    }

    @JsonCreator
    public static RecipeDiet fromValue(String value) {
        return DbEnum.fromDb(RecipeDiet.class, value);
    }

    @jakarta.persistence.Converter(autoApply = true)
    public static class Converter extends DbEnumConverter<RecipeDiet> {
        public Converter() {
            super(RecipeDiet.class);
        }
    }
}
