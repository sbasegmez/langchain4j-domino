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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.openntf.utils.TypeUtils;

public class DominoDocumentLoader {

    private static final Logger logger = Logger.getLogger(DominoDocumentLoader.class.getName());

    // Must have all of these
    private final DocumentParser documentParser;
    private final MetadataDefinition metadataDefinition;
    private final Set<String> fieldNames;

    // Order of preference:

    // Option 1: dominoDocuments
    private List<com.hcl.domino.data.Document> dominoDocuments;

    // Option 2: collectionEntries
    private List<CollectionEntry> collectionEntries;

    // Option 3.a: database
    private Database database;

    // Option 3.b: dominoClient + (server and/or databasePath)
    private DominoClient dominoClient;
    private String server;
    private String databasePath;

    // Option 3.a or 3.b + (documentUniqueId or noteId)
    private List<String> documentUniqueIds;
    private List<Integer> noteIds;

    public static DominoDocumentLoader create(MetadataDefinition metadataDefinition) {
        return new DominoDocumentLoader(metadataDefinition);
    }

    public DominoDocumentLoader(MetadataDefinition metadataDefinition) {
        this.metadataDefinition = ensureNotNull(metadataDefinition, "Metadata Definition");
        this.fieldNames = new LinkedHashSet<>();
        this.documentParser = new TextDocumentParser();
    }

    public DominoDocumentLoader fieldName(String fieldName) {
        this.fieldNames.add(fieldName);
        return this;
    }

    public DominoDocumentLoader fieldNames(Collection<String> fieldNames) {
        this.fieldNames.addAll(fieldNames);
        return this;
    }

    public DominoDocumentLoader dominoDocument(com.hcl.domino.data.Document dominoDocument) {
        return dominoDocuments(Collections.singleton(dominoDocument));
    }

    public DominoDocumentLoader dominoDocuments(Collection<com.hcl.domino.data.Document> dominoDocuments) {
        if (this.dominoDocuments == null) {
            this.dominoDocuments = new ArrayList<>();
        }

        this.dominoDocuments.addAll(dominoDocuments);
        return this;
    }

    public DominoDocumentLoader collectionEntries(CollectionEntry collectionEntries) {
        if (this.collectionEntries == null) {
            this.collectionEntries = new ArrayList<>();
        }

        this.collectionEntries.add(collectionEntries);
        return this;
    }

    public DominoDocumentLoader database(Database database) {
        this.database = database;
        return this;
    }

    public DominoDocumentLoader dominoClient(DominoClient dominoClient) {
        this.dominoClient = dominoClient;
        return this;
    }

    public DominoDocumentLoader server(String server) {
        this.server = server;
        return this;
    }

    public DominoDocumentLoader databasePath(String databasePath) {
        this.databasePath = databasePath;
        return this;
    }

    public DominoDocumentLoader documentUniqueId(String documentUniqueId) {
        return documentUniqueIds(Collections.singleton(documentUniqueId));
    }

    public DominoDocumentLoader documentUniqueIds(Collection<String> documentUniqueIds) {
        if (this.documentUniqueIds == null) {
            this.documentUniqueIds = new ArrayList<>();
        }

        this.documentUniqueIds.addAll(documentUniqueIds);
        return this;
    }

    public DominoDocumentLoader noteId(int noteId) {
        return noteIds(Collections.singleton(noteId));
    }

    public DominoDocumentLoader noteIds(Collection<Integer> noteIds) {
        if (this.noteIds == null) {
            this.noteIds = new ArrayList<>();
        }

        this.noteIds.addAll(noteIds);
        return this;
    }

    public List<Document> loadDocuments() {
        if (fieldNames.isEmpty()) {
            throw new IllegalArgumentException("At least one field name must be provided!");
        }

        // If we have dominoDocuments, we can use them directly.
        if (dominoDocuments != null && !dominoDocuments.isEmpty()) {
            return loadFromDocs(dominoDocuments);
        }

        if (collectionEntries != null && !collectionEntries.isEmpty()) {
            return loadByFetching(collectionEntries, CollectionEntry::openDocument);
        }

        if (database == null) {
            if (dominoClient == null || TypeUtils.isEmpty(databasePath)) {
                throw new IllegalArgumentException("At minimum, dominoClient and databasePath must be provided to find the Database!");
            }

            if (TypeUtils.isEmpty(server)) {
                // Hopefully, databasePath is enough
                database = dominoClient.openDatabase(databasePath);
            } else {
                database = dominoClient.openDatabase(server, databasePath);
            }
        }

        if (documentUniqueIds != null && !documentUniqueIds.isEmpty()) {
            return loadByFetching(documentUniqueIds, database::getDocumentByUNID);
        }

        if (noteIds != null && !noteIds.isEmpty()) {
            return loadByFetching(noteIds, database::getDocumentById);
        }

        // We can't return documents, then we must have an argument issue.
        throw new IllegalArgumentException("Either noteIds or collectionEntries must be provided!");
    }

    private Optional<Document> loadFromDoc(com.hcl.domino.data.Document dominoDocument) {
        DocumentSource source = DominoDocumentSource.builder()
                                                    .fieldNames(this.fieldNames)
                                                    .metadataDefinition(metadataDefinition)
                                                    .dominoDocument(dominoDocument)
                                                    .build();

        return parseSource(source, documentParser);
    }

    private List<Document> loadFromDocs(List<com.hcl.domino.data.Document> dominoDocuments) {
        return dominoDocuments.stream()
                              .map(this::loadFromDoc)
                              .flatMap(Optional::stream)
                              .toList();
    }

    /**
     * Fetches documents by IDs, attempts to load them, and returns all successfully loaded items.
     *
     * @param collection collection of parameters to send to the fetcher
     * @param fetcher    a function that takes an parameter and returns an Optional<DominoDocument>
     * @return a List of Documents
     */
    private <I> List<Document> loadByFetching(Collection<I> collection, Function<? super I, Optional<com.hcl.domino.data.Document>> fetcher) {
        if (collection == null || collection.isEmpty()) {
            return List.of();
        }
        return collection.stream()
                         .flatMap(id -> fetcher.apply(id)
                                               .flatMap(this::loadFromDoc)
                                               .stream())
                         .toList();
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
