package com.foodwise.common.enums;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum LeftoverType implements DbEnum {
    DAL("dal"),
    RICE("rice"),
    SABZI("sabzi"),
    ROTI("roti"),
    CURRY("curry"),
    OTHER("other");

    private final String dbValue;

    LeftoverType(String dbValue) {
        this.dbValue = dbValue;
    }

    @Override
    public String dbValue() {
        return dbValue;
    }

    @JsonCreator
    public static LeftoverType fromValue(String value) {
        return DbEnum.fromDb(LeftoverType.class, value);
    }

    @jakarta.persistence.Converter(autoApply = true)
    public static class Converter extends DbEnumConverter<LeftoverType> {
        public Converter() {
            super(LeftoverType.class);
        }
    }
}
