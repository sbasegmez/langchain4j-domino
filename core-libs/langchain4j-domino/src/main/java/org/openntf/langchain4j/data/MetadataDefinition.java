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
 * This class is used to extract custom metadata information from a Notes Document.
 * Metadata is useful in the document representation of Langchain4j and they are recorded into the embedding store during
 * the ingestion process. Langchain4j for Domino ships a default metadata definition with a similar content to the Domino Rest Api
 * `@meta` object.
 * <p>
 * The default metadata has `form`, `noteid`, `unid`, `created`, `addedtofile`, `lastmodified`,
 * `lastmodifiedinfile`, `lastaccessed` and `size` fields.
 * <p>
 * You can create your own metadata definition using {@link Builder}. You can also use {@link #DEFAULT} as a starting point.
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

    /**
     * Creates a Builder object.
     * @return a new builder.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Create a new builder object but start with an existing MetadataDefinition.
     * @param basedOn base MetadataDefinition.
     * @return a new builder.
     */
    public static Builder builder(MetadataDefinition basedOn) {
        return new Builder(basedOn);
    }

    /**
     * Runs a lambda for each field in the metadata definition.
     * @param consumer the lambda
     */
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

        /**
         * Add a new field to the metadata definition.
         * @param fieldName the metadata field name
         * @param formula the formula to evaluate
         * @param fieldType the field type. Currently only String, Integer, Long, Double, and Temporal are supported
         * @return the builder
         */
        private Builder addField(String fieldName, String formula, Class<?> fieldType) {
            instance.addField(new MetaField(fieldName, formula, fieldType));
            return this;
        }

        /**
         * Add a new field to the metadata definition. The formula will evaluate automatically to the field name.
         * @param fieldName the metadata field name, will be used as the formula
         * @param fieldType the field type. Currently only String, Integer, Long, Double, and Temporal are supported
         * @return the builder
         */
        private Builder addField(String fieldName, Class<?> fieldType) {
            instance.addField(new MetaField(fieldName, fieldType));
            return this;
        }

        /**
         * Add a field with type String. The formula will evaluate automatically to the field name
         * @param fieldName the metadata field name
         * @return the builder
         */
        public Builder addString(String fieldName) {
            return addField(fieldName, String.class);
        }

        /**
         * Add a field with type String and a formula
         * @param fieldName the metadata field name
         * @param formula the formula
         * @return the builder
         */
        public Builder addString(String fieldName, String formula) {
            return addField(fieldName, formula, String.class);
        }

        /**
         * Add a field with type int. The formula will evaluate automatically to the field name
         * @param fieldName the metadata field name
         * @return the builder
         */
        public Builder addInteger(String fieldName) {
            return addField(fieldName, Integer.class);
        }

        /**
         * Add a field with type int and a formula
         * @param fieldName the metadata field name
         * @param formula the formula
         * @return the builder
         */
        public Builder addInteger(String fieldName, String formula) {
            return addField(fieldName, formula, Integer.class);
        }

        /**
         * Add a field with type long, the formula will evaluate automatically to the field name
         * @param fieldName the metadata field name
         * @return the builder
         */
        public Builder addLong(String fieldName) {
            return addField(fieldName, Long.class);
        }

        /**
         * Add a field with type long and a formula
         * @param fieldName the metadata field name
         * @param formula the formula
         * @return the builder
         */
        public Builder addLong(String fieldName, String formula) {
            return addField(fieldName, formula, Long.class);
        }

        /**
         * Add a field with type double, the formula will evaluate automatically to the field name
         * @param fieldName the metadata field name
         * @return the builder
         */
        public Builder addDouble(String fieldName) {
            return addField(fieldName, Double.class);
        }

        /**
         * Add a field with type double and a formula
         * @param fieldName the metadata field name
         * @param formula the formula
         * @return the builder
         */
        public Builder addDouble(String fieldName, String formula) {
            return addField(fieldName, formula, Double.class);
        }

        /**
         * Add a field with type Temporal (Datetime). The formula will evaluate automatically to the field name
         * Langchain4j doesn't support Temporal as a metadata type yet, but we can live with a JSON date string
         * @param fieldName the metadata field name
         * @return the builder
         */
        public Builder addTemporal(String fieldName) {
            return addField(fieldName, Temporal.class);
        }

        /**
         * Add a field with type Temporal (Datetime) and a formula.
         * Langchain4j doesn't support Temporal as a metadata type yet, but we can live with a JSON date string
         * @param fieldName the metadata field name
         * @param formula the formula
         * @return the builder
         */
        public Builder addTemporal(String fieldName, String formula) {
            return addField(fieldName, formula, Temporal.class);
        }
    }
}
