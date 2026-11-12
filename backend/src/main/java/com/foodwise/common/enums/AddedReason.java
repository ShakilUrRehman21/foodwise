package com.foodwise.common.enums;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum AddedReason implements DbEnum {
    MANUAL("manual"),
    AUTO_LOW_STOCK("auto_low_stock"),
    RECIPE_MISSING("recipe_missing");

    private final String dbValue;

    AddedReason(String dbValue) {
        this.dbValue = dbValue;
    }

    @Override
    public String dbValue() {
        return dbValue;
    }

    @JsonCreator
    public static AddedReason fromValue(String value) {
        return DbEnum.fromDb(AddedReason.class, value);
    }

    @jakarta.persistence.Converter(autoApply = true)
    public static class Converter extends DbEnumConverter<AddedReason> {
        public Converter() {
            super(AddedReason.class);
        }
    }
}
