package org.openntf.langchain4j.data;

import com.hcl.domino.DominoClient;
import com.hcl.domino.commons.json.JsonUtil;
import com.hcl.domino.data.Database;
import com.hcl.domino.data.Document;
import com.hcl.domino.data.Item;
import com.hcl.domino.mime.MimeData;
import com.hcl.domino.richtext.RichTextRecordList;
import dev.langchain4j.data.document.DocumentSource;
import dev.langchain4j.data.document.Metadata;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

public class DominoDocumentSource implements DocumentSource {

    private static final String META_UNID = "unid";
    private static final String META_CREATED = "created";
    private static final String META_LASTMODIFIED = "lastmodified";
    private static final String META_LASTMODIFIEDINFILE = "lastmodifiedinfile";

    Metadata metadata;
    Document dominoDocument;
    String fieldName;

    public DominoDocumentSource(Document document, String fieldName, List<String> metadataFields) {
        this.dominoDocument = ensureNotNull(document, "Domino Document");
        this.fieldName = ensureNotNull(fieldName, "Field Name");

        buildMetadata(document, metadataFields);
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    @Override
    public InputStream inputStream() throws IOException {
        try{
            String textValue = extractText(dominoDocument, fieldName);
            return IOUtils.toInputStream(textValue, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    @Override
    public Metadata metadata() {
        return this.metadata;
    }

    private void buildMetadata(Document document, List<String> metadataFields) {
        this.metadata = new Metadata();

        metadata.put(META_UNID, document.getUNID());
        metadata.put(META_CREATED, JsonUtil.toIsoString(document.getCreated()));
        metadata.put(META_LASTMODIFIED, JsonUtil.toIsoString(document.getLastModified()));
        metadata.put(META_LASTMODIFIEDINFILE, JsonUtil.toIsoString(document.getModifiedInThisFile()));

        // TODO: Only text fields supported as this point
        for(String fieldName : metadataFields) {
            metadata.put(fieldName, document.getAsText(fieldName, ';'));
        }

    }

    private static String extractText(Document doc, String fieldName) {

        Optional<Item> item = doc.getFirstItem(fieldName);

        if(item.isPresent()) {
            switch (item.get().getType()) {
                case TYPE_COMPOSITE: // RichText
                    RichTextRecordList rtl = item.get().getValueRichText();
                    return rtl.extractText();
                case TYPE_MIME_PART: // MIME
                    MimeData mimeData = doc.get("Details", MimeData.class, null);

                    if (null != mimeData) {
                        String textData = mimeData.getPlainText();
                        if(StringUtils.isNotEmpty(textData)) {
                            return textData;
                        }

                        textData = Jsoup.parseBodyFragment(mimeData.getHtml()).text();
                        return textData;
                    }
                    break;
                default:
                    return item.get().getAsText(' ');
            }
        }

        return "";
    }

    public static class Builder {

        private String server;
        private String databasePath;
        private String documentUniqueId;
        private String fieldName;
        private Integer noteId;
        private final List<String> metadataFields;

        private DominoClient dominoClient;
        private Database database;
        private Document dominoDocument;

        public Builder() {
            this.metadataFields = new ArrayList<>();
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

        public Builder fieldName(String fieldName) {
            this.fieldName = fieldName;
            return this;
        }

        public Builder noteId(int noteId) {
            this.noteId = noteId;
            return this;
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

        public Builder addMetaField(String fieldName) {
            this.metadataFields.add(fieldName);
            return this;
        }

        public Builder addMetaFields(Collection<String> fieldNames) {
            this.metadataFields.addAll(fieldNames);
            return this;
        }

        public DominoDocumentSource build() {
            ensureNotNull(this.fieldName, "Field Name");

            // If we only have dominoDocument, it's enough
            if (dominoDocument != null) {
                return new DominoDocumentSource(dominoDocument, fieldName, this.metadataFields);
            }

            // Check if we can find the database by path
            if (dominoClient != null && StringUtils.isNotEmpty(databasePath)) {
                if (StringUtils.isNotEmpty(server)) {
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
                if (StringUtils.isNotEmpty(documentUniqueId)) {
                    optionalDocument = database.getDocumentByUNID(documentUniqueId);
                } else if (noteId!=null) {
                    optionalDocument = database.getDocumentById(noteId);
                }

                if(optionalDocument.isPresent()) {
                    return new DominoDocumentSource(optionalDocument.get(), fieldName, this.metadataFields);
                }
            }

            // Ran out of options... Throw an exception now
            throw new IllegalArgumentException("Not enough information provided.");
        }

    }


}
