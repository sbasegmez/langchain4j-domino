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
import com.hcl.domino.data.CollectionEntry;
import com.hcl.domino.data.Database;
import dev.langchain4j.data.document.BlankDocumentException;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentLoader;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.DocumentSource;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.openntf.utils.TypeUtils;

/**
 * DocumentLoader implementation for Domino documents.
 * <br>
 * This is a utility class that can be used to load document(s) from Domino databases with different options:
 * - Text values of fields
 * - Attachments
 * <br>
 * Usage:
 * - Provide a metadata definition
 * - Provide a document parser
 * - Provide a list of field names or set loadAttachments to true to load attachments (you might provide a file pattern to filter the attachments)
 * - Either:
 *   - Provide a list of Domino documents
 *   - Provide a list of collection entries
 *   - Provide a dominoClient, server and databasePath and a list of document unique ids / note ids
 *   - Provide a database and a list of document unique ids / note ids
 *
 */
public class DominoDocumentLoader {

    private static final Logger logger = Logger.getLogger(DominoDocumentLoader.class.getName());

    // Must have all of these
    private final MetadataDefinition metadataDefinition;

    // Document parser to use
    private DocumentParser documentParser;

    // Field names to be looked up
    private final Set<String> fieldNames;

    // Attached files. if true, all attachments are loaded as separate documents.
    private boolean loadAttachments;

    // Filter attachments to the file pattern
    private String filePattern;

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

    /**
     * Create a new DominoDocumentLoader with the provided metadata definition
     * @param metadataDefinition the metadata definition
     * @return a new DominoDocumentLoader
     */
    public static DominoDocumentLoader create(MetadataDefinition metadataDefinition) {
        return new DominoDocumentLoader(metadataDefinition);
    }

    /**
     * Create a new DominoDocumentLoader with the provided metadata definition
     * @param metadataDefinition the metadata definition
     */
    public DominoDocumentLoader(MetadataDefinition metadataDefinition) {
        this.fieldNames = new LinkedHashSet<>();
        this.metadataDefinition = ensureNotNull(metadataDefinition, "Metadata Definition");

        this.documentParser = new TextDocumentParser(); // default parser
        this.loadAttachments = false; // default is false
    }

    /**
     * Sets the document parser to use when loading documents.
     *
     * @param documentParser the document parser to use
     * @return this loader for method chaining
     */
    public DominoDocumentLoader documentParser(DocumentParser documentParser) {
        this.documentParser = ensureNotNull(documentParser, "Document Parser");
        return this;
    }

    /**
     * Specifies whether to load attachments from Domino documents.
     *
     * @param loadAttachments true to load attachments, false otherwise
     * @return this loader for method chaining
     */
    public DominoDocumentLoader loadAttachments(boolean loadAttachments) {
        this.loadAttachments = loadAttachments;
        return this;
    }

    /**
     * Sets the file pattern to filter attachments to be loaded.
     * Automatically enables loading attachments.
     *
     * @param pattern the file pattern (glob) for attachments, e.g. "*.pdf"
     * @return this loader for method chaining
     */
    public DominoDocumentLoader filePattern(String pattern) {
        this.loadAttachments = true;
        this.filePattern = pattern;
        return this;
    }

    /**
     * Adds a field name to be loaded from each Domino document.
     *
     * @param fieldName the field name to load
     * @return this loader for method chaining
     */
    public DominoDocumentLoader fieldName(String fieldName) {
        this.fieldNames.add(fieldName);
        return this;
    }

    /**
     * Adds multiple field names to be loaded from each Domino document.
     *
     * @param fieldNames a collection of field names to load
     * @return this loader for method chaining
     */
    public DominoDocumentLoader fieldNames(Collection<String> fieldNames) {
        this.fieldNames.addAll(fieldNames);
        return this;
    }

    /**
     * Adds a Domino document to be loaded.
     *
     * @param dominoDocument the Domino document to load
     * @return this loader for method chaining
     */
    public DominoDocumentLoader dominoDocument(com.hcl.domino.data.Document dominoDocument) {
        return dominoDocuments(Collections.singleton(dominoDocument));
    }

    /**
     * Adds multiple Domino documents to be loaded.
     *
     * @param dominoDocuments a collection of Domino documents to load
     * @return this loader for method chaining
     */
    public DominoDocumentLoader dominoDocuments(Collection<com.hcl.domino.data.Document> dominoDocuments) {
        if (this.dominoDocuments == null) {
            this.dominoDocuments = new ArrayList<>();
        }

        this.dominoDocuments.addAll(dominoDocuments);
        return this;
    }

    /**
     * Adds a collection entry whose document should be loaded.
     *
     * @param collectionEntries the collection entry to load
     * @return this loader for method chaining
     */
    public DominoDocumentLoader collectionEntries(CollectionEntry collectionEntries) {
        if (this.collectionEntries == null) {
            this.collectionEntries = new ArrayList<>();
        }

        this.collectionEntries.add(collectionEntries);
        return this;
    }

    /**
     * Specifies the Domino database to use for document lookups.
     *
     * @param database the Domino database
     * @return this loader for method chaining
     */
    public DominoDocumentLoader database(Database database) {
        this.database = database;
        return this;
    }

    /**
     * Specifies the Domino client to use for database operations.
     * Note that server and databasePath will be needed to open the database
     *
     * @param dominoClient the Domino client
     * @return this loader for method chaining
     */
    public DominoDocumentLoader dominoClient(DominoClient dominoClient) {
        this.dominoClient = dominoClient;
        return this;
    }

    /**
     * Sets the server name to use when opening the database.
     * Note that dominoClient and databasePath will be needed to open the database
     *
     * @param server the Domino server name
     * @return this loader for method chaining
     */
    public DominoDocumentLoader server(String server) {
        this.server = server;
        return this;
    }

    /**
     * Sets the database path to use when opening the database.
     * Note that dominoClient and server will be needed to open the database
     *
     * @param databasePath the Domino database path (e.g. "mail/user.nsf")
     * @return this loader for method chaining
     */
    public DominoDocumentLoader databasePath(String databasePath) {
        this.databasePath = databasePath;
        return this;
    }

    /**
     * Adds a document unique ID (UNID) to be loaded.
     *
     * @param documentUniqueId the unique document ID
     * @return this loader for method chaining
     */
    public DominoDocumentLoader documentUniqueId(String documentUniqueId) {
        return documentUniqueIds(Collections.singleton(documentUniqueId));
    }

    /**
     * Adds multiple document unique IDs (UNIDs) to be loaded.
     *
     * @param documentUniqueIds a collection of unique document IDs
     * @return this loader for method chaining
     */
    public DominoDocumentLoader documentUniqueIds(Collection<String> documentUniqueIds) {
        if (this.documentUniqueIds == null) {
            this.documentUniqueIds = new ArrayList<>();
        }

        this.documentUniqueIds.addAll(documentUniqueIds);
        return this;
    }

    /**
     * Adds a note ID to be loaded.
     *
     * @param noteId the note ID of the document
     * @return this loader for method chaining
     */
    public DominoDocumentLoader noteId(int noteId) {
        return noteIds(Collections.singleton(noteId));
    }

    /**
     * Adds multiple note IDs to be loaded.
     *
     * @param noteIds a collection of note IDs
     * @return this loader for method chaining
     */
    public DominoDocumentLoader noteIds(Collection<Integer> noteIds) {
        if (this.noteIds == null) {
            this.noteIds = new ArrayList<>();
        }

        this.noteIds.addAll(noteIds);
        return this;
    }

    /**
     * Loads documents from Domino using the provided configuration.
     * At least one source (field names, attachments, document IDs, etc.) must be specified.
     *
     * @return a list of loaded documents
     * @throws IllegalArgumentException if the configuration is incomplete or conflicting
     */
    public List<Document> loadDocuments() {
        if (loadAttachments) {
            if (!fieldNames.isEmpty()) {
                throw new IllegalArgumentException("You cannot provide field names when loading attachments!");
            }
        } else {
            if (fieldNames.isEmpty()) {
                throw new IllegalArgumentException("Either load attachments, or provide at least one field name!");
            }
        }

        // If we have dominoDocuments, we can use them directly.
        if (dominoDocuments != null && !dominoDocuments.isEmpty()) {
            return loadFromDocs(dominoDocuments);
        }

        if (collectionEntries != null && !collectionEntries.isEmpty()) {
            return loadByFetching(collectionEntries, CollectionEntry::openDocument);
        }

        boolean closeDatabase = false;

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

            // If we open the database, we need to close it when done.
            closeDatabase = true;
        }

        try {
            if (documentUniqueIds != null && !documentUniqueIds.isEmpty()) {
                return loadByFetching(documentUniqueIds, database::getDocumentByUNID);
            }

            if (noteIds != null && !noteIds.isEmpty()) {
                return loadByFetching(noteIds, database::getDocumentById);
            }
        } finally {
            if (closeDatabase) {
                database.close();
            }
        }

        // We can't return documents, then we must have an argument issue.
        throw new IllegalArgumentException("Either noteIds or collectionEntries must be provided!");
    }

    private Optional<Document> loadFieldsFromDoc(com.hcl.domino.data.Document dominoDocument) {
        DocumentSource source = DominoDataDocumentSource.builder()
                                                        .fieldNames(this.fieldNames)
                                                        .metadataDefinition(metadataDefinition)
                                                        .dominoDocument(dominoDocument)
                                                        .build();

        return parseSource(source, documentParser);
    }

    private List<Document> loadAttachmentsFromDoc(com.hcl.domino.data.Document dominoDocument) {
        if(TypeUtils.isEmpty(this.filePattern)) {
            this.filePattern = "*.*";
        }
        PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + filePattern.toLowerCase(Locale.ENGLISH));

        return dominoDocument.getAttachmentNames()
                             .stream()
                             .filter(attachmentName -> matcher.matches(Paths.get(attachmentName.toLowerCase(Locale.ENGLISH))))
                             .map(attachmentName -> loadAttachmentFromDoc(dominoDocument, attachmentName))
                             .flatMap(Optional::stream)
                             .toList();
    }

    private Optional<Document> loadAttachmentFromDoc(com.hcl.domino.data.Document dominoDocument, String attachmentName) {
        DocumentSource source = DominoAttachmentDocumentSource.builder()
                                                              .attachment(attachmentName)
                                                              .metadataDefinition(metadataDefinition)
                                                              .dominoDocument(dominoDocument)
                                                              .build();

        return parseSource(source, documentParser);
    }

    private List<Document> loadFromDocs(Collection<com.hcl.domino.data.Document> dominoDocuments) {

        if (loadAttachments) {
            return dominoDocuments.stream()
                                  .map(this::loadAttachmentsFromDoc)
                                  .flatMap(List::stream)
                                  .toList();
        }

        return dominoDocuments.stream()
                              .map(this::loadFieldsFromDoc)
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

        if (loadAttachments) {
            return collection.stream()
                             .flatMap(id -> fetcher.apply(id)
                                                   .map(this::loadAttachmentsFromDoc)
                                                   .stream()
                                                   .flatMap(List::stream))
                             .toList();
        }

        return collection.stream()
                         .flatMap(id -> fetcher.apply(id)
                                               .flatMap(this::loadFieldsFromDoc)
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
