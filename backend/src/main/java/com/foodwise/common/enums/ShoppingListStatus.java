package com.foodwise.common.enums;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum ShoppingListStatus implements DbEnum {
    ACTIVE("active"),
    ARCHIVED("archived");

    private final String dbValue;

    ShoppingListStatus(String dbValue) {
        this.dbValue = dbValue;
    }

    @Override
    public String dbValue() {
        return dbValue;
    }

    @JsonCreator
    public static ShoppingListStatus fromValue(String value) {
        return DbEnum.fromDb(ShoppingListStatus.class, value);
    }

    @jakarta.persistence.Converter(autoApply = true)
    public static class Converter extends DbEnumConverter<ShoppingListStatus> {
        public Converter() {
            super(ShoppingListStatus.class);
        }
    }
}
