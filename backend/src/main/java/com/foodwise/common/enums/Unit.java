package com.foodwise.common.enums;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum Unit implements DbEnum {
    G("g"),
    KG("kg"),
    ML("ml"),
    L("L"),
    PCS("pcs"),
    PKT("pkt"),
    BUNCH("bunch"),
    TBSP("tbsp"),
    TSP("tsp"),
    PINCH("pinch");

    private final String dbValue;

    Unit(String dbValue) {
        this.dbValue = dbValue;
    }

    @Override
    public String dbValue() {
        return dbValue;
    }

    @JsonCreator
    public static Unit fromValue(String value) {
        return DbEnum.fromDb(Unit.class, value);
    }

    @jakarta.persistence.Converter(autoApply = true)
    public static class Converter extends DbEnumConverter<Unit> {
        public Converter() {
            super(Unit.class);
        }
    }
}
