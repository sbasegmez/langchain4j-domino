/*
 * Copyright (c) ${project.inceptionYear}-2025 Serdar Basegmez
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
import com.hcl.domino.data.Item;
import com.hcl.domino.mime.MimeData;
import com.hcl.domino.richtext.RichTextRecordList;
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
import org.jsoup.Jsoup;
import org.openntf.utils.TypeUtils;

public class DominoDocumentSource implements DocumentSource {

    private static final Logger log = Logger.getLogger(DominoDocumentSource.class.getName());

    private final MetadataDefinition metadataDefinition;
    private final Metadata metadata;
    private final Document dominoDocument;
    private final Set<String> fieldNames;

    public DominoDocumentSource(Document document, String fieldName) {
        this(document, Set.of(fieldName));
    }

    public DominoDocumentSource(Document document, Set<String> fieldNames) {
        this(document, fieldNames, MetadataDefinition.DEFAULT);
    }

    public DominoDocumentSource(Document document, String fieldName, MetadataDefinition metadataDefinition) {
        this(document, Set.of(fieldName), metadataDefinition);
    }

    public DominoDocumentSource(Document document, Set<String> fieldNames, MetadataDefinition metadataDefinition) {
        this.dominoDocument = ensureNotNull(document, "Domino Document");
        this.metadataDefinition = ensureNotNull(metadataDefinition, "Metadata Definition");

        this.fieldNames = new LinkedHashSet<>(ensureNotNull(fieldNames, "Field Names"));

        // Build Metadata
        this.metadata = new Metadata();
        metadataDefinition.forEachField(fieldDefinition -> addToMetadata(metadata, document, fieldDefinition));
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public InputStream inputStream() throws IOException {
        try {
            // TODO: Large documents may cause duplicate memory impact. Consider ReaderInputStream
            return new ByteArrayInputStream(extractText().getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    @Override
    public Metadata metadata() {
        return this.metadata;
    }

    public MetadataDefinition getMetadataDefinition() {
        return this.metadataDefinition;
    }

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

    private String extractText() {
        StringBuilder text = new StringBuilder();

        for (String fieldName : fieldNames) {
            String fieldText = extractFieldText(dominoDocument, fieldName);
            if (TypeUtils.isNotEmpty(fieldText)) {
                if (!text.isEmpty()) {
                    text.append("\n");
                }
                text.append(fieldText);
            }
        }

        return text.toString();
    }

    private static String extractFieldText(Document doc, String fieldName) {
        Optional<Item> item = doc.getFirstItem(fieldName);

        if (item.isEmpty()) {
            // No item found, return empty string
            return "";
        }

        switch (item.get().getType()) {
            case TYPE_COMPOSITE: // RichText
                RichTextRecordList rtl = item.get()
                                             .getValueRichText();
                return rtl.extractText();

            case TYPE_MIME_PART: // MIME
                MimeData mimeData = doc.get(fieldName, MimeData.class, null);

                if (null != mimeData) {
                    String textData = mimeData.getPlainText();
                    if (TypeUtils.isNotEmpty(textData)) {
                        return textData;
                    }

                    textData = Jsoup.parseBodyFragment(mimeData.getHtml())
                                    .text();
                    return textData;
                }
                return "";

            default:
                return item.get().getAsText(' ');

        }
    }

    public static class Builder {

        private String server;
        private String databasePath;
        private String documentUniqueId;
        private Integer noteId;
        private MetadataDefinition metadataDefinition;

        private DominoClient dominoClient;
        private Database database;
        private Document dominoDocument;

        private final Set<String> fieldNames;

        public Builder() {
            this.fieldNames = new LinkedHashSet<>();
        }

        public Builder dominoClient(DominoClient dominoClient) {
            this.dominoClient = dominoClient;
            return this;
        }

        public Builder database(Database database) {
            this.database = database;
            return this;
        }

        public Builder dominoDocument(Document dominoDocument) {
            this.dominoDocument = dominoDocument;
            return this;
        }

        public Builder server(String server) {
            this.server = server;
            return this;
        }

        public Builder databasePath(String databasePath) {
            this.databasePath = databasePath;
            return this;
        }

        public Builder documentUniqueId(String documentUniqueId) {
            this.documentUniqueId = documentUniqueId;
            return this;
        }

        public Builder noteId(int noteId) {
            this.noteId = noteId;
            return this;
        }

        public Builder fieldName(String fieldName) {
            this.fieldNames.add(fieldName);
            return this;
        }

        public Builder fieldNames(Collection<String> fieldNames) {
            this.fieldNames.addAll(fieldNames);
            return this;
        }

        public Builder metadataDefinition(MetadataDefinition metadataDefinition) {
            this.metadataDefinition = metadataDefinition;
            return this;
        }

        /**
         * Valid combination of parameters:
         * dominoClient + (server and/or databasePath) + (documentUniqueId or noteId)
         * database + (documentUniqueId or noteId)
         * dominoDocument
         *
         * @return DominoDocumentSource
         */
        public DominoDocumentSource build() {
            if (this.metadataDefinition == null) {
                this.metadataDefinition = MetadataDefinition.DEFAULT;
            }

            // If we only have dominoDocument, it's enough
            if (dominoDocument != null) {
                return new DominoDocumentSource(dominoDocument, fieldNames, metadataDefinition);
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
                Optional<Document> optionalDocument = Optional.empty();

                // More options to find the document might be implemented
                if (TypeUtils.isNotEmpty(documentUniqueId)) {
                    optionalDocument = database.getDocumentByUNID(documentUniqueId);
                } else if (noteId != null) {
                    optionalDocument = database.getDocumentById(noteId);
                }

                if (optionalDocument.isPresent()) {
                    return new DominoDocumentSource(optionalDocument.get(), fieldNames, metadataDefinition);
                }
            }

            // Ran out of options... Throw an exception now
            throw new IllegalArgumentException("Not enough information provided.");
        }

    }


}
