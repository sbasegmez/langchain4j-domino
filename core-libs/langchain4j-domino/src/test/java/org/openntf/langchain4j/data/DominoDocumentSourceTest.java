package org.openntf.langchain4j.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hcl.domino.commons.json.JsonUtil;
import com.hcl.domino.data.Database;
import com.hcl.domino.data.Document;
import com.hcl.domino.mime.MimeData;
import com.hcl.domino.mime.attachments.UrlMimeAttachment;
import com.hcl.domino.richtext.RichTextWriter;
import com.hcl.domino.richtext.TextStyle.Justify;
import dev.langchain4j.data.document.DocumentSource;
import dev.langchain4j.data.document.Metadata;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openntf.test.jnx.AbstractNotesRuntimeTest;

class DominoDocumentSourceTest extends AbstractNotesRuntimeTest {

    private static final ThreadLocal<Database> threadDb = new ThreadLocal<>();

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
            System.err.println("Unable to delete database " + tempDest + ": " + t);
        } finally {
            threadDb.remove();
        }
    }

    private Database getTempDb() {
        return threadDb.get();
    }

    private String getContent(DocumentSource documentSource) throws IOException {
        return new String(documentSource.inputStream().readAllBytes(), StandardCharsets.UTF_8);
    }

    private String htmlToText(String html) {
        // Simple HTML to text conversion
        return Jsoup.parseBodyFragment(html).text();
    }

    @Test
    void testDocumentSource() throws Exception {

        String form = "Test";
        String title = "Test Document";
        String description = "This is a test document.";

        Document doc = getTempDb().createDocument()
                                  .replaceItemValue("Form", form)
                                  .replaceItemValue("Title", title)
                                  .replaceItemValue("Description", description);

        String bodyRtContent = "Lorem Ipsum.";
        try (RichTextWriter w = doc.createRichTextItem("BodyRT")) {
            w.addText(bodyRtContent, w.createTextStyle("center").setAlign(Justify.CENTER), w.createFontStyle().setBold(true));
        }

        // This bit comes from https://github.com/HCL-TECH-SOFTWARE/domino-jnx/blob/develop/test/it-domino-jnx/src/test/java/it/com/hcl/domino/test/mime/TestMimeDataItemType.java
        MimeData writtenMimeData = new MimeData();

        // Attach an image
        String cid = writtenMimeData.embed(new UrlMimeAttachment(this.getClass().getResource("/test.png")));

        // Create HTML content
        String html = "<html><body>This is <b>formatted</b> text and an image:<br><img src=\"cid:" + cid + "\"></body></html>";
        writtenMimeData.setHtml(html);

        // Plain text
        String plainText = "This is alternative plaintext";
        writtenMimeData.setPlainText(plainText);

        // Attach a PDF
        writtenMimeData.attach(new UrlMimeAttachment(this.getClass().getResource("/test.pdf")));

        doc.replaceItemValue("BodyMimeMixed", writtenMimeData);

        writtenMimeData = new MimeData();
        writtenMimeData.setHtml(html);
        doc.replaceItemValue("BodyMimeHtml", writtenMimeData);

        doc.save();

        // Plain text field
        var docSrc = DominoDocumentSource.builder()
                                         .dominoDocument(doc)
                                         .fieldName("Description")
                                         .build();

        // Check default metadata
        Metadata metadata = docSrc.metadata();
        assertEquals(form, metadata.getString("form"), "Form mismatch");
        assertEquals(doc.getNoteID(), Long.parseLong(Objects.requireNonNull(metadata.getString("noteid")), 16), "NoteID mismatch");
        assertEquals(doc.getUNID(), metadata.getString("unid"), "UNID mismatch");
        assertEquals(JsonUtil.toIsoString(doc.getCreated()), metadata.getString("created"), "Created mismatch");
        assertEquals(JsonUtil.toIsoString(doc.getAddedToFile()), metadata.getString("addedtofile"), "Created mismatch");
        assertEquals(JsonUtil.toIsoString(doc.getLastModified()), metadata.getString("lastmodified"), "Created mismatch");
        assertEquals(JsonUtil.toIsoString(doc.getModifiedInThisFile()), metadata.getString("lastmodifiedinfile"), "Created mismatch");
        assertEquals(JsonUtil.toIsoString(doc.getLastAccessed()), metadata.getString("lastaccessed"), "Created mismatch");
        assertEquals(doc.size(), metadata.getLong("size"), "Doc size mismatch");

        // Check plain text content
        assertEquals(description, getContent(docSrc), "Plain Text field content mismatch");

        // Order of fields should be preserved
        docSrc = DominoDocumentSource.builder()
                                     .dominoDocument(doc)
                                     .metadataDefinition(MetadataDefinition.EMPTY)
                                     .fieldName("Title")
                                     .fieldName("Form")
                                     .fieldName("Description")
                                     .fieldName("BodyRT")
                                     .build();

        // Empty metadata definition
        assertTrue(docSrc.metadata().toMap().isEmpty(), "Empty Metadata should be empty");

        String expected = title + "\n" + form + "\n" + description + "\n" + bodyRtContent;
        assertEquals(expected, getContent(docSrc), "Multi Text field content mismatch");

        // Mime converson
        docSrc = DominoDocumentSource.builder()
                                     .metadataDefinition(MetadataDefinition.EMPTY)
                                     .dominoDocument(doc)
                                     .fieldName("BodyMimeMixed")
                                     .build();

        assertEquals(plainText, getContent(docSrc), "Mixed Mime should extract Plain Text content");

        // Mime converson
        docSrc = DominoDocumentSource.builder()
                                     .metadataDefinition(MetadataDefinition.EMPTY)
                                     .dominoDocument(doc)
                                     .fieldName("BodyMimeHtml")
                                     .build();

        assertEquals(htmlToText(html), getContent(docSrc), "Mixed Mime should extract Html Text content");
    }


}
