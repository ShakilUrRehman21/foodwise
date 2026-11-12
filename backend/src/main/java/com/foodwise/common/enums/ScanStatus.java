package com.foodwise.common.enums;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum ScanStatus implements DbEnum {
    PENDING("pending"),
    CONFIRMED("confirmed"),
    REJECTED("rejected"),
    FAILED("failed");

    private final String dbValue;

    ScanStatus(String dbValue) {
        this.dbValue = dbValue;
    }

    @Override
    public String dbValue() {
        return dbValue;
    }

    @JsonCreator
    public static ScanStatus fromValue(String value) {
        return DbEnum.fromDb(ScanStatus.class, value);
    }

    @jakarta.persistence.Converter(autoApply = true)
    public static class Converter extends DbEnumConverter<ScanStatus> {
        public Converter() {
            super(ScanStatus.class);
        }
    }
}
