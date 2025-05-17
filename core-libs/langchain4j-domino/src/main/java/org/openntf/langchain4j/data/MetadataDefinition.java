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

import dev.langchain4j.internal.ValidationUtils;
import java.time.temporal.Temporal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * This class facilitates easy creation of metadata definitions for domino documents.
 * Each Field
 */
public class MetadataDefinition {

    private final Map<String, MetaField> fields;

    /**
     * Creates a default metadata definition for Domino documents.
     * We tried our best to create a definition that is compatible with the Domino REST API.
     */
    public static MetadataDefinition DEFAULT = new Builder()
        .addString("form")
        .addString("noteid", "@RightBack(@NoteID;\"NT\")")
        .addString("unid", "@Text(@DocumentUniqueID)")
        .addTemporal("created", "@Created")
        .addTemporal("addedtofile", "@AddedToThisFile")
        .addTemporal("lastmodified", "@Modified")
        .addTemporal("lastmodifiedinfile", "@ModifiedInThisFile")
        .addTemporal("lastaccessed", "@Accessed")
        .addInteger("size", "@DocLength")
        .build();

    /**
     * Empty metadata definition.
     */
    public static MetadataDefinition EMPTY = new Builder().build();

    private MetadataDefinition() {
        this.fields = new LinkedHashMap<>();
    }

    private void addField(MetaField metaField) {
        // We don't check for duplicates here, as the same field name can be used in different formulas
        fields.put(metaField.fieldName(), metaField);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static Builder builder(MetadataDefinition basedOn) {
        return new Builder(basedOn);
    }

    public void forEachField(Consumer<MetaField> consumer) {
        fields.values().forEach(consumer);
    }

    public static class Builder {
        private final MetadataDefinition instance;

        private Builder() {
            this.instance = new MetadataDefinition();
        }

        private Builder(MetadataDefinition basedOn) {
            this();

            this.instance.fields.putAll(ValidationUtils.ensureNotNull(basedOn, "basedOn").fields);
        }

        public MetadataDefinition build() {
            return instance;
        }

        private Builder addField(String fieldName, String formula, Class<?> fieldType) {
            instance.addField(new MetaField(fieldName, formula, fieldType));
            return this;
        }

        private Builder addField(String fieldName, Class<?> fieldType) {
            instance.addField(new MetaField(fieldName, fieldType));
            return this;
        }
        // Add a field with type String

        public Builder addString(String fieldName) {
            return addField(fieldName, String.class);
        }
        // Add a field with type String and a formula

        public Builder addString(String fieldName, String formula) {
            return addField(fieldName, formula, String.class);
        }
        // Add a field with type int

        public Builder addInteger(String fieldName) {
            return addField(fieldName, Integer.class);
        }
        // Add a field with type int and a formula

        public Builder addInteger(String fieldName, String formula) {
            return addField(fieldName, formula, Integer.class);
        }
        // Add a field with type long

        public Builder addLong(String fieldName) {
            return addField(fieldName, Long.class);
        }
        // Add a field with type long and a formula

        public Builder addLong(String fieldName, String formula) {
            return addField(fieldName, formula, Long.class);
        }
        // Add a field with type double

        public Builder addDouble(String fieldName) {
            return addField(fieldName, Double.class);
        }
        // Add a field with type double and a formula

        public Builder addDouble(String fieldName, String formula) {
            return addField(fieldName, formula, Double.class);
        }
        // Add a field with type Temporal (Datetime)
        // Langchain4j doesn't support Temporal as a metadata type yet, but we can live with Long

        public Builder addTemporal(String fieldName) {
            return addField(fieldName, Temporal.class);
        }
        // Add a field with type Temporal (Datetime) and a formula
        // Langchain4j doesn't support Temporal as a metadata type yet, but we can live with Long

        public Builder addTemporal(String fieldName, String formula) {
            return addField(fieldName, formula, Temporal.class);
        }
    }
}
