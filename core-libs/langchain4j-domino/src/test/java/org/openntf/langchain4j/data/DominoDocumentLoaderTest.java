package org.openntf.langchain4j.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.hcl.domino.data.Attachment.Compression;
import com.hcl.domino.data.Database;
import com.hcl.domino.data.Document.IAttachmentProducer;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import dev.langchain4j.data.document.parser.apache.pdfbox.ApachePdfBoxDocumentParser;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openntf.test.jnx.AbstractNotesRuntimeTest;
import org.openntf.utils.TypeUtils;

class DominoDocumentLoaderTest extends AbstractNotesRuntimeTest {

    private static final ThreadLocal<Database> threadDb = new ThreadLocal<>();

    private static final List<String> TEST_TITLES = List.of(
        "Lorem ipsum dolor sit amet",
        "Consectetur adipiscing elit",
        "Sed do eiusmod tempor incididunt ut labore et dolore magna aliqua"
    );

    private static final List<String> TEST_CONTENTS = List.of(
        "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.",
        "Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat.",
        "Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur."
    );

    @BeforeEach
    void setup() throws IOException {
        threadDb.set(createTempDb(getClient()));
    }

    @AfterEach
    void tearDown() {
        Database db = threadDb.get();
        String tempDest = db.getAbsoluteFilePath();

        // Close the temp database
        db.close();
        try {
            getClient().deleteDatabase(null, tempDest);
        } catch (final Throwable t) {
            System.err.println("Could not delete database " + tempDest + ": " + t);
        } finally {
            threadDb.remove();
        }
    }

    private Database getTempDb() {
        return threadDb.get();
    }

    private List<com.hcl.domino.data.Document> createTestDocuments(Database db) {
        List<com.hcl.domino.data.Document> docs = new ArrayList<>();

        for (int i = 0; i < TEST_TITLES.size(); i++) {
            docs.add(createTestDocument(db, i));
        }

        return docs;
    }

    private static com.hcl.domino.data.Document createTestDocument(Database db, int idx) {
        var doc = db.createDocument();

        doc.replaceItemValue("Title", TEST_TITLES.get(idx));
        doc.replaceItemValue("Content", TEST_CONTENTS.get(idx));

        doc.save();

        return doc;
    }

    private static void attachToDoc(com.hcl.domino.data.Document doc, String name, String content) {
        // We don't really care how attachment is attached
        doc.attachFile(name, Instant.now(), Instant.now(),
                       new IAttachmentProducer() {

                           @Override
                           public long getSizeEstimation() {
                               return -1;
                           }

                           @Override
                           public void produceAttachment(final OutputStream out) throws IOException {
                               out.write(content.getBytes(StandardCharsets.UTF_8));
                           }
                       });
        doc.save();
    }

    @Test
    void testExceptionCases() {
        Database db = getTempDb();

        var doc = createTestDocument(db, 0);

        assertThrows(IllegalArgumentException.class,
                     () -> DominoDocumentLoader.create(MetadataDefinition.DEFAULT)
                                               .fieldNames(List.of("Title", "Content"))
                                               .loadDocuments(),
                     "Expected IllegalArgumentException no document or documents provided");

        assertThrows(IllegalArgumentException.class,
                     () -> DominoDocumentLoader.create(MetadataDefinition.DEFAULT)
                                               .dominoDocument(doc)
                                               .loadDocuments(),
                     "Expected IllegalArgumentException when passing document without field or attachment directives");

        assertThrows(IllegalArgumentException.class,
                     () -> DominoDocumentLoader.create(MetadataDefinition.DEFAULT)
                                               .fieldNames(List.of("Title", "Content"))
                                               .loadAttachments(true)
                                               .dominoDocument(doc)
                                               .loadDocuments(),
                     "Expected IllegalArgumentException when passing document fields and attachments together");

        assertThrows(IllegalArgumentException.class,
                     () -> DominoDocumentLoader.create(MetadataDefinition.DEFAULT)
                                               .loadAttachments(true)
                                               .database(db)
                                               .loadDocuments(),
                     "Expected IllegalArgumentException only database provided");

        assertThrows(IllegalArgumentException.class,
                     () -> DominoDocumentLoader.create(MetadataDefinition.DEFAULT)
                                               .loadAttachments(true)
                                               .dominoClient(getClient())
                                               .loadDocuments(),
                     "Expected IllegalArgumentException only dominoClient provided");

        assertThrows(IllegalArgumentException.class,
                     () -> DominoDocumentLoader.create(MetadataDefinition.DEFAULT)
                                               .loadAttachments(true)
                                               .dominoClient(getClient())
                                               .databasePath(db.getAbsoluteFilePath())
                                               .loadDocuments(),
                     "Expected IllegalArgumentException only dominoClient and databasePath provided");

    }


    @Test
    void testLoadSingleDocumentWithFields() {
        Database db = getTempDb();

        List<Document> docs = DominoDocumentLoader.create(MetadataDefinition.DEFAULT)
                                                  .fieldNames(List.of("Title", "Content"))
                                                  .dominoDocument(createTestDocument(db, 0))
                                                  .loadDocuments();

        assertEquals(1, docs.size(), "Expected 1 document to be loaded");

        Document doc = docs.get(0);
        String expectedText = TEST_TITLES.get(0) + "\n" + TEST_CONTENTS.get(0);
        assertEquals(expectedText, doc.text(), "Expected text to match");
    }

    @Test
    void testLoadMultipleDocumentsWithFields() {
        Database db = getTempDb();

        List<Document> docs = DominoDocumentLoader.create(MetadataDefinition.DEFAULT)
                                                  .fieldNames(List.of("Title", "Content"))
                                                  .dominoDocuments(createTestDocuments(db))
                                                  .loadDocuments();

        assertEquals(TEST_TITLES.size(), docs.size(), "Expected number of documents to be loaded");

        for (int i = 0; i < TEST_TITLES.size(); i++) {
            Document doc = docs.get(i);
            String expectedText = TEST_TITLES.get(i) + "\n" + TEST_CONTENTS.get(i);
            assertEquals(expectedText, doc.text(), "Expected text to match for document " + i);
        }
    }

    @Test
    void testSingleDocWithAttachments() {
        Database db = getTempDb();
        var doc = createTestDocument(db, 0);

        for (int i = 0; i < TEST_CONTENTS.size(); i++) {
            attachToDoc(doc, "test" + i + ".txt", TEST_CONTENTS.get(i));
        }

        List<Document> docs = DominoDocumentLoader.create(MetadataDefinition.DEFAULT)
                                                  .documentParser(new TextDocumentParser())
                                                  .loadAttachments(true)
                                                  .dominoDocument(doc)
                                                  .loadDocuments();

        for (int i = 0; i < TEST_CONTENTS.size(); i++) {
            Document doc1 = docs.get(i);
            String expectedText = TEST_CONTENTS.get(i);
            assertEquals(expectedText, doc1.text(), "Expected text to match for document " + i);
        }
    }

    @Test
    void testSingleDocWithPDFParser() throws URISyntaxException {
        Database db = getTempDb();
        var doc = createTestDocument(db, 0);

        // Attach a PDF file to the document
        Path attPath = Path.of(Objects.requireNonNull(this.getClass().getResource("/test1.pdf")).toURI());
        doc.attachFile(attPath.toString(), attPath.getFileName().toString(), Compression.NONE);

        List<Document> docs = DominoDocumentLoader.create(MetadataDefinition.DEFAULT)
                                                  .documentParser(new ApachePdfBoxDocumentParser())
                                                  .loadAttachments(true)
                                                  .dominoDocument(doc)
                                                  .loadDocuments();

        assertEquals(1, docs.size(), "Expected 1 document to be loaded");
        Document doc1 = docs.get(0);

        // We know the content of the PDF file but there will be whitespaces
        List<String> expectedLines = List.of(
            "Lorem Ipsum",
            "Lorem ipsum dolor sit amet, consectetur adipiscing elit");

        List<String> result = Arrays.stream(doc1.text().split("\n"))
                                    .map(String::trim)
                                    .filter(TypeUtils::isNotEmpty)
                                    .toList();

        assertEquals(expectedLines, result, "Expected text to match for attachment");
    }

}
