package com.wiki.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Service;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Service for extracting text content from documents using Apache Tika.
 * Supports PDF, Word (DOC/DOCX), Excel, PowerPoint, and many other formats.
 */
@Service
@Slf4j
public class TextExtractionService {

    private final Tika tika;
    private final Parser parser;

    // Supported MIME types for text extraction
    private static final Set<String> SUPPORTED_TYPES = Set.of(
            // PDF
            "application/pdf",
            // Microsoft Word
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            // Microsoft Excel
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            // Microsoft PowerPoint
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            // OpenDocument formats
            "application/vnd.oasis.opendocument.text",
            "application/vnd.oasis.opendocument.spreadsheet",
            "application/vnd.oasis.opendocument.presentation",
            // Rich Text Format
            "application/rtf",
            // Plain text
            "text/plain",
            // HTML
            "text/html",
            // XML
            "application/xml",
            "text/xml"
    );

    // Maximum characters to extract (to prevent memory issues with large documents)
    private static final int MAX_CONTENT_LENGTH = 10_000_000; // 10MB of text

    public TextExtractionService() {
        this.tika = new Tika();
        this.parser = new AutoDetectParser();
    }

    /**
     * Extract text content from a document.
     *
     * @param inputStream the document input stream
     * @param filename    the original filename (used for type detection)
     * @return extracted text content
     */
    public ExtractionResult extractText(InputStream inputStream, String filename) {
        log.info("Extracting text from: {}", filename);

        try {
            // Detect content type
            String contentType = tika.detect(filename);
            log.debug("Detected content type: {}", contentType);

            if (!isSupported(contentType)) {
                log.warn("Unsupported content type for text extraction: {}", contentType);
                return ExtractionResult.unsupported(contentType);
            }

            // Set up the content handler with a character limit
            BodyContentHandler handler = new BodyContentHandler(MAX_CONTENT_LENGTH);
            Metadata metadata = new Metadata();
            metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, filename);
            ParseContext context = new ParseContext();

            // Parse the document
            parser.parse(inputStream, handler, metadata, context);

            String extractedText = handler.toString().trim();
            Map<String, String> extractedMetadata = extractMetadata(metadata);

            log.info("Successfully extracted {} characters from: {}",
                    extractedText.length(), filename);

            return ExtractionResult.success(extractedText, extractedMetadata, contentType);

        } catch (TikaException e) {
            log.error("Tika parsing error for {}: {}", filename, e.getMessage());
            return ExtractionResult.error("Parsing error: " + e.getMessage());
        } catch (SAXException e) {
            log.error("SAX parsing error for {}: {}", filename, e.getMessage());
            return ExtractionResult.error("XML parsing error: " + e.getMessage());
        } catch (IOException e) {
            log.error("IO error reading {}: {}", filename, e.getMessage());
            return ExtractionResult.error("IO error: " + e.getMessage());
        }
    }

    /**
     * Extract text content from a document with content type hint.
     *
     * @param inputStream the document input stream
     * @param filename    the original filename
     * @param contentType the content type (MIME type)
     * @return extracted text content
     */
    public ExtractionResult extractText(InputStream inputStream, String filename, String contentType) {
        log.info("Extracting text from: {} (type: {})", filename, contentType);

        if (!isSupported(contentType)) {
            log.warn("Unsupported content type for text extraction: {}", contentType);
            return ExtractionResult.unsupported(contentType);
        }

        try {
            BodyContentHandler handler = new BodyContentHandler(MAX_CONTENT_LENGTH);
            Metadata metadata = new Metadata();
            metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, filename);
            metadata.set(Metadata.CONTENT_TYPE, contentType);
            ParseContext context = new ParseContext();

            parser.parse(inputStream, handler, metadata, context);

            String extractedText = handler.toString().trim();
            Map<String, String> extractedMetadata = extractMetadata(metadata);

            log.info("Successfully extracted {} characters from: {}",
                    extractedText.length(), filename);

            return ExtractionResult.success(extractedText, extractedMetadata, contentType);

        } catch (TikaException e) {
            log.error("Tika parsing error for {}: {}", filename, e.getMessage());
            return ExtractionResult.error("Parsing error: " + e.getMessage());
        } catch (SAXException e) {
            log.error("SAX parsing error for {}: {}", filename, e.getMessage());
            return ExtractionResult.error("XML parsing error: " + e.getMessage());
        } catch (IOException e) {
            log.error("IO error reading {}: {}", filename, e.getMessage());
            return ExtractionResult.error("IO error: " + e.getMessage());
        }
    }

    /**
     * Check if a content type is supported for text extraction.
     */
    public boolean isSupported(String contentType) {
        if (contentType == null) {
            return false;
        }
        // Check exact match or base type match (ignoring parameters)
        String baseType = contentType.split(";")[0].trim().toLowerCase();
        return SUPPORTED_TYPES.contains(baseType);
    }

    /**
     * Detect the content type of a file.
     */
    public String detectContentType(InputStream inputStream, String filename) throws IOException {
        return tika.detect(inputStream, filename);
    }

    /**
     * Extract metadata from Tika metadata object.
     */
    private Map<String, String> extractMetadata(Metadata metadata) {
        Map<String, String> result = new HashMap<>();

        // Extract common metadata fields
        addMetadataIfPresent(result, "title", metadata.get(TikaCoreProperties.TITLE));
        addMetadataIfPresent(result, "author", metadata.get(TikaCoreProperties.CREATOR));
        addMetadataIfPresent(result, "created", metadata.get(TikaCoreProperties.CREATED));
        addMetadataIfPresent(result, "modified", metadata.get(TikaCoreProperties.MODIFIED));
        addMetadataIfPresent(result, "pageCount", metadata.get("xmpTPg:NPages"));
        addMetadataIfPresent(result, "wordCount", metadata.get("meta:word-count"));

        return result;
    }

    private void addMetadataIfPresent(Map<String, String> map, String key, String value) {
        if (value != null && !value.isBlank()) {
            map.put(key, value);
        }
    }

    /**
     * Result of text extraction operation.
     */
    public static class ExtractionResult {
        private final boolean successful;
        private final String text;
        private final Map<String, String> metadata;
        private final String contentType;
        private final String errorMessage;
        private final boolean unsupportedType;

        private ExtractionResult(boolean successful, String text, Map<String, String> metadata,
                                 String contentType, String errorMessage, boolean unsupportedType) {
            this.successful = successful;
            this.text = text;
            this.metadata = metadata != null ? metadata : new HashMap<>();
            this.contentType = contentType;
            this.errorMessage = errorMessage;
            this.unsupportedType = unsupportedType;
        }

        public static ExtractionResult success(String text, Map<String, String> metadata, String contentType) {
            return new ExtractionResult(true, text, metadata, contentType, null, false);
        }

        public static ExtractionResult error(String errorMessage) {
            return new ExtractionResult(false, null, null, null, errorMessage, false);
        }

        public static ExtractionResult unsupported(String contentType) {
            return new ExtractionResult(false, null, null, contentType,
                    "Unsupported content type: " + contentType, true);
        }

        public boolean isSuccessful() {
            return successful;
        }

        public String getText() {
            return text;
        }

        public Map<String, String> getMetadata() {
            return metadata;
        }

        public String getContentType() {
            return contentType;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public boolean isUnsupportedType() {
            return unsupportedType;
        }

        public int getTextLength() {
            return text != null ? text.length() : 0;
        }
    }
}