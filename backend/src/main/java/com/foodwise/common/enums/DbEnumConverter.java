package com.foodwise.common.enums;

import jakarta.persistence.AttributeConverter;

/** Base JPA converter between an UPPERCASE Java enum and its lowercase database string. */
public abstract class DbEnumConverter<E extends Enum<E> & DbEnum> implements AttributeConverter<E, String> {

    private final Class<E> type;

    protected DbEnumConverter(Class<E> type) {
        this.type = type;
    }

    @Override
    public String convertToDatabaseColumn(E attribute) {
        return attribute == null ? null : attribute.dbValue();
    }

    @Override
    public E convertToEntityAttribute(String dbData) {
        return DbEnum.fromDb(type, dbData);
    }
}
