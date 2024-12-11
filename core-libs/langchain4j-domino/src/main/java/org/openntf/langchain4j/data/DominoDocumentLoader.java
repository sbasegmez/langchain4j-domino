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

import com.hcl.domino.data.CollectionEntry;
import com.hcl.domino.data.Database;
import dev.langchain4j.data.document.BlankDocumentException;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentLoader;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.DocumentSource;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.lang3.StringUtils;

public class DominoDocumentLoader {

    private static final Logger logger = Logger.getLogger(DominoDocumentLoader.class.getName());

    private String fieldName;
    private String documentUniqueId;
    private Integer noteId;
    private final List<String> metaFields;
    private Set<Integer> noteIds;

    private DocumentParser documentParser;

    private Database database;
    private com.hcl.domino.data.Document dominoDocument;
    private List<CollectionEntry> collectionEntries;

    public static DominoDocumentLoader newLoader() {
        return new DominoDocumentLoader();
    }

    public DominoDocumentLoader() {
        this.fieldName = "Body";
        this.metaFields = new ArrayList<>();
        this.documentParser = new TextDocumentParser();
    }

    public DominoDocumentLoader documentUniqueId(String documentUniqueId) {
        this.documentUniqueId = documentUniqueId;
        return this;
    }

    public DominoDocumentLoader fieldName(String fieldName) {
        this.fieldName = ensureNotNull(fieldName, "Field Name");
        return this;
    }

    public DominoDocumentLoader noteId(int noteId) {
        this.noteId = noteId;
        return this;
    }

    public DominoDocumentLoader documentParser(DocumentParser documentParser) {
        this.documentParser = ensureNotNull(this.documentParser, "Document Parser");
        return this;
    }

    public DominoDocumentLoader database(Database database) {
        this.database = database;
        return this;
    }

    public DominoDocumentLoader dominoDocument(com.hcl.domino.data.Document dominoDocument) {
        this.dominoDocument = dominoDocument;
        return this;
    }

    public DominoDocumentLoader addMetaField(String fieldName) {
        this.metaFields.add(fieldName);
        return this;
    }

    public DominoDocumentLoader addMetaFields(Collection<String> fieldNames) {
        this.metaFields.addAll(fieldNames);
        return this;
    }

    public DominoDocumentLoader noteIds(Set<Integer> noteIds) {
        this.noteIds = Collections.unmodifiableSet(noteIds);
        return this;
    }

    public DominoDocumentLoader collectionEntries(List<CollectionEntry> collectionEntries) {
        this.collectionEntries = Collections.unmodifiableList(collectionEntries);
        return this;
    }

    // Single document loader
    public Optional<Document> loadDocument() {
        DominoDocumentSource.Builder builder = DominoDocumentSource.newBuilder()
                                                                   .fieldName(fieldName)
                                                                   .addMetaFields(metaFields);

        if (dominoDocument != null) {
            builder.dominoDocument(dominoDocument);
        }

        if (database != null) {
            builder.database(database);

            if (StringUtils.isNotEmpty(documentUniqueId)) {
                builder.documentUniqueId(documentUniqueId);
            } else if (noteId != null) {
                builder.noteId(noteId);
            }
        }

        return parseSource(builder.build(), documentParser);
    }

    public List<Document> loadDocuments() {
        final List<Document> documents = new ArrayList<>();

        if (database != null && noteIds != null && !noteIds.isEmpty()) {
            noteIds.forEach(noteId -> {
                DocumentSource source = DominoDocumentSource.newBuilder()
                                                            .fieldName(fieldName)
                                                            .addMetaFields(metaFields)
                                                            .database(database)
                                                            .noteId(noteId)
                                                            .build();

                parseSource(source, documentParser).ifPresent(documents::add);
            });

            return documents;
        }

        if (collectionEntries != null && !collectionEntries.isEmpty()) {
            collectionEntries.forEach(collectionEntry -> {
                if (collectionEntry.isDocument()) {
                    collectionEntry.openDocument()
                                   .ifPresent(document -> {
                                       DocumentSource source = DominoDocumentSource.newBuilder()
                                                                                   .fieldName(fieldName)
                                                                                   .addMetaFields(metaFields)
                                                                                   .dominoDocument(document)
                                                                                   .build();
                                       parseSource(source, documentParser).ifPresent(documents::add);
                                   });

                }
            });

            return documents;
        }

        // We can't return documents, then we must have an argument issue.
        throw new IllegalArgumentException("Either noteIds or collectionEntries must be provided!");
    }

    private static Optional<Document> parseSource(DocumentSource source, DocumentParser documentParser) {
        try {
            return Optional.of(DocumentLoader.load(source, documentParser));
        } catch (BlankDocumentException e) {
            logger.log(Level.WARNING, "Blank document found, skipping...");
        }

        return Optional.empty();
    }

}