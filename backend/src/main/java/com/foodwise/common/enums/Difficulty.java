package com.foodwise.common.enums;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum Difficulty implements DbEnum {
    EASY("easy"),
    MEDIUM("medium"),
    HARD("hard");

    private final String dbValue;

    Difficulty(String dbValue) {
        this.dbValue = dbValue;
    }

    @Override
    public String dbValue() {
        return dbValue;
    }

    @JsonCreator
    public static Difficulty fromValue(String value) {
        return DbEnum.fromDb(Difficulty.class, value);
    }

    @jakarta.persistence.Converter(autoApply = true)
    public static class Converter extends DbEnumConverter<Difficulty> {
        public Converter() {
            super(Difficulty.class);
        }
    }
}
