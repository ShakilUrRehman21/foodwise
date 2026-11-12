package com.foodwise.common.enums;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum KitchenRole implements DbEnum {
    OWNER("owner"),
    MEMBER("member");

    private final String dbValue;

    KitchenRole(String dbValue) {
        this.dbValue = dbValue;
    }

    @Override
    public String dbValue() {
        return dbValue;
    }

    @JsonCreator
    public static KitchenRole fromValue(String value) {
        return DbEnum.fromDb(KitchenRole.class, value);
    }

    @jakarta.persistence.Converter(autoApply = true)
    public static class Converter extends DbEnumConverter<KitchenRole> {
        public Converter() {
            super(KitchenRole.class);
        }
    }
}
