package com.foodwise.common.enums;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum InventoryEventType implements DbEnum {
    CREATED("created"),
    CONSUMED_PARTIALLY("consumed_partially"),
    CONSUMED_FULLY("consumed_fully"),
    DISCARDED_SPOILED("discarded_spoiled"),
    DISCARDED_EXPIRED("discarded_expired"),
    QUANTITY_ADJUSTED("quantity_adjusted"),
    LOCATION_MOVED("location_moved");

    private final String dbValue;

    InventoryEventType(String dbValue) {
        this.dbValue = dbValue;
    }

    @Override
    public String dbValue() {
        return dbValue;
    }

    @JsonCreator
    public static InventoryEventType fromValue(String value) {
        return DbEnum.fromDb(InventoryEventType.class, value);
    }

    @jakarta.persistence.Converter(autoApply = true)
    public static class Converter extends DbEnumConverter<InventoryEventType> {
        public Converter() {
            super(InventoryEventType.class);
        }
    }
}
