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

import com.hcl.domino.data.Attachment;
import com.hcl.domino.data.Document;
import java.io.IOException;
import java.io.InputStream;
import org.openntf.langchain4j.data.DominoDataDocumentSource.Builder;
import org.openntf.utils.TypeUtils;

/**
 * This class is an implementation of the interface that extracts text from a file attached to a Domino document.
 * It can use Langchain4j's parsers to detach and parse attachment content to create a Langchain4j document representation.
 * <p>
 * You can use {@link DominoDataDocumentSource.Builder} to create an instance of this class.
 * <p>
 * This is just to access single file attachment on a Domino document. The common use case is to use {@link DominoDocumentLoader}
 * which will use this class as needed.
 */
public class DominoAttachmentDocumentSource extends AbstractDominoDocumentSource {

    private final String attachmentName;

    /**
     * Creates a new DominoAttachmentDocumentSource from a Domino document and an attachment name.
     * Uses a default {@link MetadataDefinition} instance.
     *
     * @param document       the Domino document to extract text from.
     * @param attachmentName the name of the attachment to extract text from.
     */
    public DominoAttachmentDocumentSource(Document document, String attachmentName) {
        this(document, MetadataDefinition.DEFAULT, attachmentName);
    }

    /**
     * Creates a new DominoAttachmentDocumentSource from a Domino document, Metadata definition and an attachment name.
     *
     * @param document           the Domino document to extract text from.
     * @param metadataDefinition the Metadata definition to use.
     * @param attachmentName     the name of the attachment to extract text from.
     */
    public DominoAttachmentDocumentSource(Document document, MetadataDefinition metadataDefinition, String attachmentName) {
        super(document, metadataDefinition);

        if (TypeUtils.isEmpty(attachmentName)) {
            throw new IllegalArgumentException("Attachment name cannot be null or empty");
        }

        this.attachmentName = attachmentName;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public InputStream doInputStream() throws IOException {
        Attachment attachment = dominoDocument.getAttachment(attachmentName)
                                              .orElseThrow(
                                                  () -> new IllegalArgumentException("Attachment not found: " + attachmentName));

        return attachment.getInputStream();
    }

    public static class Builder extends AbstractBuilder<Builder> {

        private String attachmentName;

        public Builder() {
            super();
        }

        /**
         * Sets the attachment name.
         *
         * @param attachmentName the name of the attachment to extract text from.
         * @return this builder instance, for method chaining.
         */
        public Builder attachment(String attachmentName) {
            this.attachmentName = attachmentName;
            return this;
        }

        @Override
        public Builder self() {
            return this;
        }

        @Override
        public DominoAttachmentDocumentSource build() {
            Document document = findDominoDocument().orElseThrow(() -> new IllegalArgumentException("Insufficient arguments to create a DominoDataDocumentSource"));
            return new DominoAttachmentDocumentSource(document, metadataDefinition, attachmentName);
        }
    }
}
