package com.foodwise.common.enums;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum BatchStatus implements DbEnum {
    ACTIVE("active"),
    CONSUMED("consumed"),
    DISCARDED("discarded"),
    SPOILED("spoiled");

    private final String dbValue;

    BatchStatus(String dbValue) {
        this.dbValue = dbValue;
    }

    @Override
    public String dbValue() {
        return dbValue;
    }

    @JsonCreator
    public static BatchStatus fromValue(String value) {
        return DbEnum.fromDb(BatchStatus.class, value);
    }

    @jakarta.persistence.Converter(autoApply = true)
    public static class Converter extends DbEnumConverter<BatchStatus> {
        public Converter() {
            super(BatchStatus.class);
        }
    }
}
