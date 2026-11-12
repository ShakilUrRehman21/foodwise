package com.foodwise.common.enums;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum WasteReason implements DbEnum {
    EXPIRED("expired"),
    SPOILED("spoiled"),
    COOKED_TOO_MUCH("cooked_too_much"),
    FORGOTTEN_LEFTOVER("forgotten_leftover"),
    BAD_TASTE("bad_taste");

    private final String dbValue;

    WasteReason(String dbValue) {
        this.dbValue = dbValue;
    }

    @Override
    public String dbValue() {
        return dbValue;
    }

    @JsonCreator
    public static WasteReason fromValue(String value) {
        return DbEnum.fromDb(WasteReason.class, value);
    }

    @jakarta.persistence.Converter(autoApply = true)
    public static class Converter extends DbEnumConverter<WasteReason> {
        public Converter() {
            super(WasteReason.class);
        }
    }
}
