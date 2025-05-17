/*
 * Copyright (c) 2024-2025 Serdar Basegmez
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
