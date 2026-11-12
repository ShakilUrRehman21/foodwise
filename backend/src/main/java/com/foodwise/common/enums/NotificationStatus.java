package com.foodwise.common.enums;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum NotificationStatus implements DbEnum {
    UNREAD("unread"),
    READ("read"),
    ARCHIVED("archived");

    private final String dbValue;

    NotificationStatus(String dbValue) {
        this.dbValue = dbValue;
    }

    @Override
    public String dbValue() {
        return dbValue;
    }

    @JsonCreator
    public static NotificationStatus fromValue(String value) {
        return DbEnum.fromDb(NotificationStatus.class, value);
    }

    @jakarta.persistence.Converter(autoApply = true)
    public static class Converter extends DbEnumConverter<NotificationStatus> {
        public Converter() {
            super(NotificationStatus.class);
        }
    }
}
