package com.foodwise.common.enums;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum ScanType implements DbEnum {
    RECEIPT("receipt"),
    GROCERY_HAUL("grocery_haul"),
    FRIDGE_SHELF("fridge_shelf");

    private final String dbValue;

    ScanType(String dbValue) {
        this.dbValue = dbValue;
    }

    @Override
    public String dbValue() {
        return dbValue;
    }

    @JsonCreator
    public static ScanType fromValue(String value) {
        return DbEnum.fromDb(ScanType.class, value);
    }

    @jakarta.persistence.Converter(autoApply = true)
    public static class Converter extends DbEnumConverter<ScanType> {
        public Converter() {
            super(ScanType.class);
        }
    }
}
