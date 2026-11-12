package com.foodwise.common.enums;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum NotificationType implements DbEnum {
    EXPIRY_WARNING("expiry_warning"),
    ITEM_EXPIRED("item_expired"),
    STOCK_LOW("stock_low"),
    LEFTOVER_ALERT("leftover_alert"),
    WASTE_SUMMARY("waste_summary");

    private final String dbValue;

    NotificationType(String dbValue) {
        this.dbValue = dbValue;
    }

    @Override
    public String dbValue() {
        return dbValue;
    }

    @JsonCreator
    public static NotificationType fromValue(String value) {
        return DbEnum.fromDb(NotificationType.class, value);
    }

    @jakarta.persistence.Converter(autoApply = true)
    public static class Converter extends DbEnumConverter<NotificationType> {
        public Converter() {
            super(NotificationType.class);
        }
    }
}
