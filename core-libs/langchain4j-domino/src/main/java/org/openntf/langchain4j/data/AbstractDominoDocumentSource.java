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

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import com.hcl.domino.DominoClient;
import com.hcl.domino.commons.json.JsonUtil;
import com.hcl.domino.data.Database;
import com.hcl.domino.data.Document;
import com.hcl.domino.data.DominoDateTime;
import com.hcl.domino.data.Formula;
import dev.langchain4j.data.document.DocumentSource;
import dev.langchain4j.data.document.Metadata;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.temporal.Temporal;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;
import org.openntf.utils.TypeUtils;

public abstract class AbstractDominoDocumentSource implements DocumentSource {

    private static final Logger log = Logger.getLogger(AbstractDominoDocumentSource.class.getName());

    protected final MetadataDefinition metadataDefinition;
    protected final Metadata metadata;
    protected final Document dominoDocument;

    protected AbstractDominoDocumentSource(Document dominoDocument, MetadataDefinition metadataDefinition) {
        super();
        this.dominoDocument = ensureNotNull(dominoDocument, "Domino Document");
        this.metadataDefinition = ensureNotNull(metadataDefinition, "Metadata Definition");

        this.metadata = new Metadata();

        // Build Metadata
        metadataDefinition.forEachField(fieldDefinition -> addToMetadata(metadata, dominoDocument, fieldDefinition));
    }

    @Override
    public Metadata metadata() {
        return this.metadata;
    }

    @Override
    public InputStream inputStream() throws IOException {
        try {
            // Delegate to the subclass implementation
            return doInputStream();
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    public MetadataDefinition getMetadataDefinition() {
        return this.metadataDefinition;
    }

    protected abstract InputStream doInputStream() throws IOException;

    private static void addToMetadata(Metadata metadata, Document document, MetaField fieldDefinition) {
        String fieldName = fieldDefinition.fieldName();
        String formulaStr = fieldDefinition.formula();
        Class<?> fieldType = fieldDefinition.fieldType();

        // TODO Error checking needed for formulas
        Formula formula = document.getParentDatabase().getParentDominoClient().createFormula(formulaStr);

        List<Object> values = null;
        try {
            values = formula.evaluate(document);
        } catch (Exception e) {
            log.warning("Error evaluating formula: " + formulaStr + " - " + e.getMessage());
        }

        if (TypeUtils.isEmpty(values)) {
            // No value found, skip
            return;
        }

        // We support only one value per field
        Object value = values.get(0);

        // JNX can return String, Integer, Long, Double, DominoDateTime
        // We know all field types are OK with metadata, except for Temporal
        if (fieldType.equals(Temporal.class)) {
            if (value instanceof DominoDateTime) {
                // Store datetime value as ISO string
                metadata.put(fieldName, JsonUtil.toIsoString((DominoDateTime) value));
            } // Ignore non-Temporal values
        } else if (fieldType.equals(String.class)) {
            if (value instanceof String) {
                metadata.put(fieldName, (String) value);
            } else if (null != value) {
                metadata.put(fieldName, String.valueOf(value));
            } // Ignore null values
        } else if (fieldType.equals(Integer.class)) {
            if (value instanceof Number number) {
                metadata.put(fieldName, number.intValue());
            } // Ignore non-numeric values
        } else if (fieldType.equals(Long.class)) {
            if (value instanceof Number number) {
                metadata.put(fieldName, number.longValue());
            } // Ignore non-numeric values
        } else if (fieldType.equals(Double.class)) {
            if (value instanceof Number number) {
                metadata.put(fieldName, number.doubleValue());
            } // Ignore non-numeric values
        }
    }

    protected abstract static class AbstractBuilder<T extends AbstractBuilder<T>> {

        protected String server;
        protected String databasePath;
        protected String documentUniqueId;
        protected Integer noteId;
        protected MetadataDefinition metadataDefinition;

        protected DominoClient dominoClient;
        protected Database database;
        protected Document dominoDocument;

        public AbstractBuilder() {
        }

        public T dominoClient(DominoClient dominoClient) {
            this.dominoClient = dominoClient;
            return self();
        }

        public T database(Database database) {
            this.database = database;
            return self();
        }

        public T dominoDocument(Document dominoDocument) {
            this.dominoDocument = dominoDocument;
            return self();
        }

        public T server(String server) {
            this.server = server;
            return self();
        }

        public T databasePath(String databasePath) {
            this.databasePath = databasePath;
            return self();
        }

        public T documentUniqueId(String documentUniqueId) {
            this.documentUniqueId = documentUniqueId;
            return self();
        }

        public T noteId(int noteId) {
            this.noteId = noteId;
            return self();
        }

        public T metadataDefinition(MetadataDefinition metadataDefinition) {
            this.metadataDefinition = metadataDefinition;
            return self();
        }

        /**
         * Subclasses will receive Domino Document using this method.
         *
         * Valid combination of parameters:
         * dominoClient + (server and/or databasePath) + (documentUniqueId or noteId)
         * database + (documentUniqueId or noteId)
         * dominoDocument
         *
         * @return Document
         */
        protected Optional<Document> findDominoDocument() {
            if (this.metadataDefinition == null) {
                this.metadataDefinition = MetadataDefinition.DEFAULT;
            }

            // If we only have dominoDocument, it's enough
            if (dominoDocument != null) {
                return Optional.of(dominoDocument);
            }

            // Check if we can find the database by path
            if (dominoClient != null && TypeUtils.isNotEmpty(databasePath)) {
                if (TypeUtils.isNotEmpty(server)) {
                    this.database = dominoClient.openDatabase(server, databasePath);
                } else {
                    // Assuming databasePath is accessible without server
                    this.database = dominoClient.openDatabase(databasePath);
                }
            }

            // Either way, database should be available by now
            if (database != null) {
                // More options to find the document might be implemented
                if (TypeUtils.isNotEmpty(documentUniqueId)) {
                    return database.getDocumentByUNID(documentUniqueId);
                } else if (noteId != null) {
                    return database.getDocumentById(noteId);
                }
            }

            // Ran out of options... Throw an exception now
            return Optional.empty();
        }

        protected abstract T self();
        public abstract AbstractDominoDocumentSource build();
    }

}
