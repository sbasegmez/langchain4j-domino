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

import com.hcl.domino.data.Document;
import com.hcl.domino.data.Item;
import com.hcl.domino.mime.MimeData;
import com.hcl.domino.richtext.RichTextRecordList;
import dev.langchain4j.data.document.DocumentSource;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import org.jsoup.Jsoup;
import org.openntf.utils.TypeUtils;

/**
 * A {@link DocumentSource} implementation that extracts text from a Domino document's fields.
 * This class is used to create a document source from a Domino document, allowing for the extraction of text from specific fields.
 * It is particularly useful for handling documents with rich text or MIME content.
 *
 * You can use {@link Builder} to create an instance of this class.
 */
public class DominoDataDocumentSource extends AbstractDominoDocumentSource {

    private final Set<String> fieldNames;

    public DominoDataDocumentSource(Document document, String fieldName) {
        this(document, Set.of(fieldName));
    }

    public DominoDataDocumentSource(Document document, Set<String> fieldNames) {
        this(document, MetadataDefinition.DEFAULT, fieldNames);
    }

    public DominoDataDocumentSource(Document document, MetadataDefinition metadataDefinition, String fieldName) {
        this(document, metadataDefinition, Set.of(fieldName));
    }

    public DominoDataDocumentSource(Document document, MetadataDefinition metadataDefinition, Set<String> fieldNames) {
        super(document, metadataDefinition);

        this.fieldNames = new LinkedHashSet<>(ensureNotNull(fieldNames, "Field Names"));
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public InputStream doInputStream() {
        // TODO: Large documents may cause duplicate memory impact. Consider ReaderInputStream
        return new ByteArrayInputStream(extractText().getBytes(StandardCharsets.UTF_8));
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

    public static class Builder extends AbstractBuilder<Builder> {

        private final Set<String> fieldNames;

        public Builder() {
            super();
            this.fieldNames = new LinkedHashSet<>();
        }

        public Builder fieldName(String fieldName) {
            this.fieldNames.add(fieldName);
            return this;
        }

        public Builder fieldNames(Collection<String> fieldNames) {
            this.fieldNames.addAll(fieldNames);
            return this;
        }

        @Override
        public Builder self() {
            return this;
        }

        @Override
        public DominoDataDocumentSource build() {
            Document document = findDominoDocument().orElseThrow(() -> new IllegalArgumentException("Insufficient arguments to create a DominoDataDocumentSource"));
            return new DominoDataDocumentSource(document, metadataDefinition, fieldNames);
        }
    }
}
