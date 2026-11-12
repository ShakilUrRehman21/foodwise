package com.foodwise.common.enums;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum StorageLocation implements DbEnum {
    FRIDGE("fridge"),
    FREEZER("freezer"),
    PANTRY("pantry");

    private final String dbValue;

    StorageLocation(String dbValue) {
        this.dbValue = dbValue;
    }

    @Override
    public String dbValue() {
        return dbValue;
    }

    @JsonCreator
    public static StorageLocation fromValue(String value) {
        return DbEnum.fromDb(StorageLocation.class, value);
    }

    @jakarta.persistence.Converter(autoApply = true)
    public static class Converter extends DbEnumConverter<StorageLocation> {
        public Converter() {
            super(StorageLocation.class);
        }
    }
}
