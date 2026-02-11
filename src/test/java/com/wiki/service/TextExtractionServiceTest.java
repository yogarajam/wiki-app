package com.wiki.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for TextExtractionService
 */
@DisplayName("Text Extraction Service Tests")
class TextExtractionServiceTest {

    private TextExtractionService textExtractionService;

    @BeforeEach
    void setUp() {
        textExtractionService = new TextExtractionService();
    }

    @Nested
    @DisplayName("Content Type Support Tests")
    class ContentTypeSupportTests {

        @Test
        @DisplayName("Should support PDF content type")
        void shouldSupportPdf() {
            assertThat(textExtractionService.isSupported("application/pdf")).isTrue();
        }

        @Test
        @DisplayName("Should support Word DOCX content type")
        void shouldSupportDocx() {
            assertThat(textExtractionService.isSupported(
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                    .isTrue();
        }

        @Test
        @DisplayName("Should support Word DOC content type")
        void shouldSupportDoc() {
            assertThat(textExtractionService.isSupported("application/msword")).isTrue();
        }

        @Test
        @DisplayName("Should support Excel XLSX content type")
        void shouldSupportXlsx() {
            assertThat(textExtractionService.isSupported(
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .isTrue();
        }

        @Test
        @DisplayName("Should support plain text content type")
        void shouldSupportPlainText() {
            assertThat(textExtractionService.isSupported("text/plain")).isTrue();
        }

        @Test
        @DisplayName("Should support HTML content type")
        void shouldSupportHtml() {
            assertThat(textExtractionService.isSupported("text/html")).isTrue();
        }

        @Test
        @DisplayName("Should not support image content types")
        void shouldNotSupportImages() {
            assertThat(textExtractionService.isSupported("image/png")).isFalse();
            assertThat(textExtractionService.isSupported("image/jpeg")).isFalse();
            assertThat(textExtractionService.isSupported("image/gif")).isFalse();
        }

        @Test
        @DisplayName("Should not support video content types")
        void shouldNotSupportVideo() {
            assertThat(textExtractionService.isSupported("video/mp4")).isFalse();
        }

        @Test
        @DisplayName("Should handle null content type")
        void shouldHandleNullContentType() {
            assertThat(textExtractionService.isSupported(null)).isFalse();
        }

        @Test
        @DisplayName("Should handle content type with parameters")
        void shouldHandleContentTypeWithParameters() {
            assertThat(textExtractionService.isSupported("text/plain; charset=UTF-8")).isTrue();
        }
    }

    @Nested
    @DisplayName("Plain Text Extraction Tests")
    class PlainTextExtractionTests {

        @Test
        @DisplayName("Should extract text from plain text file")
        void shouldExtractFromPlainText() {
            String content = "This is a test document with some sample text.";
            InputStream inputStream = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));

            TextExtractionService.ExtractionResult result =
                    textExtractionService.extractText(inputStream, "test.txt", "text/plain");

            assertThat(result.isSuccessful()).isTrue();
            assertThat(result.getText()).contains("test document");
            assertThat(result.getText()).contains("sample text");
        }

        @Test
        @DisplayName("Should extract text from HTML content")
        void shouldExtractFromHtml() {
            String html = """
                    <html>
                    <head><title>Test Page</title></head>
                    <body>
                        <h1>Hello World</h1>
                        <p>This is a paragraph with some text.</p>
                    </body>
                    </html>
                    """;
            InputStream inputStream = new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8));

            TextExtractionService.ExtractionResult result =
                    textExtractionService.extractText(inputStream, "test.html", "text/html");

            assertThat(result.isSuccessful()).isTrue();
            assertThat(result.getText()).contains("Hello World");
            assertThat(result.getText()).contains("paragraph");
        }

        @Test
        @DisplayName("Should handle empty content gracefully")
        void shouldHandleEmptyContent() {
            InputStream inputStream = new ByteArrayInputStream(new byte[0]);

            TextExtractionService.ExtractionResult result =
                    textExtractionService.extractText(inputStream, "empty.txt", "text/plain");

            // Tika may either succeed with empty text or fail gracefully on empty input
            if (result.isSuccessful()) {
                assertThat(result.getText()).isEmpty();
            } else {
                assertThat(result.getErrorMessage()).isNotNull();
            }
        }
    }

    @Nested
    @DisplayName("Unsupported Content Tests")
    class UnsupportedContentTests {

        @Test
        @DisplayName("Should return unsupported result for images")
        void shouldReturnUnsupportedForImages() {
            InputStream inputStream = new ByteArrayInputStream(new byte[0]);

            TextExtractionService.ExtractionResult result =
                    textExtractionService.extractText(inputStream, "test.png", "image/png");

            assertThat(result.isSuccessful()).isFalse();
            assertThat(result.isUnsupportedType()).isTrue();
            assertThat(result.getErrorMessage()).contains("Unsupported");
        }
    }

    @Nested
    @DisplayName("Extraction Result Tests")
    class ExtractionResultTests {

        @Test
        @DisplayName("Success result should have all properties set")
        void successResultShouldHaveAllProperties() {
            TextExtractionService.ExtractionResult result =
                    TextExtractionService.ExtractionResult.success(
                            "Test content",
                            java.util.Map.of("title", "Test Title"),
                            "text/plain"
                    );

            assertThat(result.isSuccessful()).isTrue();
            assertThat(result.getText()).isEqualTo("Test content");
            assertThat(result.getMetadata()).containsEntry("title", "Test Title");
            assertThat(result.getContentType()).isEqualTo("text/plain");
            assertThat(result.getTextLength()).isEqualTo(12);
            assertThat(result.getErrorMessage()).isNull();
        }

        @Test
        @DisplayName("Error result should have error message")
        void errorResultShouldHaveErrorMessage() {
            TextExtractionService.ExtractionResult result =
                    TextExtractionService.ExtractionResult.error("Something went wrong");

            assertThat(result.isSuccessful()).isFalse();
            assertThat(result.getErrorMessage()).isEqualTo("Something went wrong");
            assertThat(result.getText()).isNull();
        }

        @Test
        @DisplayName("Unsupported result should be marked as unsupported")
        void unsupportedResultShouldBeMarked() {
            TextExtractionService.ExtractionResult result =
                    TextExtractionService.ExtractionResult.unsupported("image/png");

            assertThat(result.isSuccessful()).isFalse();
            assertThat(result.isUnsupportedType()).isTrue();
            assertThat(result.getContentType()).isEqualTo("image/png");
        }
    }
}