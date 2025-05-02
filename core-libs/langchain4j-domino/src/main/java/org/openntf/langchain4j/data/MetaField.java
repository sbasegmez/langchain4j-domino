package org.openntf.langchain4j.data;

import org.openntf.utils.TypeUtils;

public record MetaField(String fieldName, String formula, Class<?> fieldType) {

    public MetaField {
        if (TypeUtils.isEmpty(fieldName)) {
            throw new IllegalArgumentException("Field name cannot be null or empty");
        }
        if (TypeUtils.isEmpty(formula)) {
            // If no formula is provided, use the field name as the formula
            throw new IllegalArgumentException("Formula cannot be null or empty");
        }
        if (fieldType == null) {
            throw new IllegalArgumentException("Field type cannot be null");
        }
    }

    public MetaField(String fieldName, Class<?> fieldType) {
        // If no formula is provided, use the field name as the formula
        this(fieldName, fieldName, fieldType);
    }
}
