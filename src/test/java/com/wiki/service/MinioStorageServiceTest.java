package com.wiki.service;

import com.wiki.exception.StorageException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for MinioStorageService using Testcontainers
 * Tests PDF document upload and retrieval with a real MinIO instance
 */
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("MinIO Storage Service Integration Tests")
class MinioStorageServiceTest {

    private static final String MINIO_ACCESS_KEY = "minioadmin";
    private static final String MINIO_SECRET_KEY = "minioadmin";
    private static final String BUCKET_NAME = "wiki-attachments";

    @Container
    static MinIOContainer minioContainer = new MinIOContainer("minio/minio:latest")
            .withUserName(MINIO_ACCESS_KEY)
            .withPassword(MINIO_SECRET_KEY);

    private static S3Client s3Client;
    private static MinioStorageService storageService;

    // Sample PDF content (minimal valid PDF)
    private static final byte[] SAMPLE_PDF_CONTENT = createSamplePdfContent();

    @BeforeAll
    static void setUpAll() {
        // Create S3 client connected to the MinIO container
        s3Client = S3Client.builder()
                .endpointOverride(URI.create(minioContainer.getS3URL()))
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(MINIO_ACCESS_KEY, MINIO_SECRET_KEY)))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .build())
                .build();

        // Create the storage service with test configuration
        storageService = new MinioStorageService(s3Client);

        // Use reflection to set the bucket name (or create a constructor/setter)
        setFieldValue(storageService, "bucketName", BUCKET_NAME);
        setFieldValue(storageService, "maxRetries", 3);
    }

    @AfterAll
    static void tearDownAll() {
        if (s3Client != null) {
            s3Client.close();
        }
    }

    @Test
    @Order(1)
    @DisplayName("Should successfully upload a PDF document to MinIO")
    void shouldUploadPdfDocument() {
        // Given
        MultipartFile pdfFile = new MockMultipartFile(
                "document",
                "test-document.pdf",
                "application/pdf",
                SAMPLE_PDF_CONTENT
        );

        // When
        String objectKey = storageService.uploadFile(pdfFile);

        // Then
        assertThat(objectKey)
                .isNotNull()
                .isNotBlank()
                .contains("test-document.pdf");

        // Verify file exists in MinIO
        assertThat(storageService.fileExists(objectKey)).isTrue();
    }

    @Test
    @Order(2)
    @DisplayName("Should successfully retrieve uploaded PDF document from MinIO")
    void shouldRetrievePdfDocument() throws IOException {
        // Given - Upload a PDF first
        MultipartFile pdfFile = new MockMultipartFile(
                "document",
                "retrieve-test.pdf",
                "application/pdf",
                SAMPLE_PDF_CONTENT
        );
        String objectKey = storageService.uploadFile(pdfFile);

        // When - Retrieve the file directly using S3 client
        GetObjectRequest getRequest = GetObjectRequest.builder()
                .bucket(BUCKET_NAME)
                .key(objectKey)
                .build();

        ResponseInputStream<GetObjectResponse> response = s3Client.getObject(getRequest);
        byte[] retrievedContent = readAllBytes(response);

        // Then
        assertThat(retrievedContent)
                .isNotNull()
                .isNotEmpty()
                .isEqualTo(SAMPLE_PDF_CONTENT);

        GetObjectResponse metadata = response.response();
        assertThat(metadata.contentType()).isEqualTo("application/pdf");
    }

    @Test
    @Order(3)
    @DisplayName("Should upload PDF with custom path prefix")
    void shouldUploadPdfWithPathPrefix() {
        // Given
        MultipartFile pdfFile = new MockMultipartFile(
                "document",
                "wiki-page-doc.pdf",
                "application/pdf",
                SAMPLE_PDF_CONTENT
        );
        String pathPrefix = "wiki-pages/123/attachments";

        // When
        String objectKey = storageService.uploadFile(pdfFile, pathPrefix);

        // Then
        assertThat(objectKey)
                .isNotNull()
                .startsWith(pathPrefix)
                .contains("wiki-page-doc.pdf");

        assertThat(storageService.fileExists(objectKey)).isTrue();
    }

    @Test
    @Order(4)
    @DisplayName("Should delete PDF document from MinIO")
    void shouldDeletePdfDocument() {
        // Given - Upload a PDF first
        MultipartFile pdfFile = new MockMultipartFile(
                "document",
                "to-delete.pdf",
                "application/pdf",
                SAMPLE_PDF_CONTENT
        );
        String objectKey = storageService.uploadFile(pdfFile);
        assertThat(storageService.fileExists(objectKey)).isTrue();

        // When
        storageService.deleteFile(objectKey);

        // Then
        assertThat(storageService.fileExists(objectKey)).isFalse();
    }

    @Test
    @Order(5)
    @DisplayName("Should upload large PDF using multipart upload")
    void shouldUploadLargePdfUsingMultipartUpload() {
        // Given - Create a large file (11MB to trigger multipart upload)
        byte[] largePdfContent = createLargePdfContent(11 * 1024 * 1024);
        MultipartFile largePdfFile = new MockMultipartFile(
                "large-document",
                "large-document.pdf",
                "application/pdf",
                largePdfContent
        );

        // When
        String objectKey = storageService.uploadFile(largePdfFile);

        // Then
        assertThat(objectKey)
                .isNotNull()
                .contains("large-document.pdf");

        assertThat(storageService.fileExists(objectKey)).isTrue();

        // Verify file size
        HeadObjectRequest headRequest = HeadObjectRequest.builder()
                .bucket(BUCKET_NAME)
                .key(objectKey)
                .build();
        HeadObjectResponse headResponse = s3Client.headObject(headRequest);
        assertThat(headResponse.contentLength()).isEqualTo(largePdfContent.length);
    }

    @Test
    @Order(6)
    @DisplayName("Should return correct file URL")
    void shouldReturnCorrectFileUrl() {
        // Given
        MultipartFile pdfFile = new MockMultipartFile(
                "document",
                "url-test.pdf",
                "application/pdf",
                SAMPLE_PDF_CONTENT
        );
        String objectKey = storageService.uploadFile(pdfFile);

        // When
        String fileUrl = storageService.getFileUrl(objectKey);

        // Then
        assertThat(fileUrl)
                .isNotNull()
                .contains(BUCKET_NAME)
                .contains(objectKey);
    }

    @Test
    @Order(7)
    @DisplayName("Should throw exception for empty file")
    void shouldThrowExceptionForEmptyFile() {
        // Given
        MultipartFile emptyFile = new MockMultipartFile(
                "document",
                "empty.pdf",
                "application/pdf",
                new byte[0]
        );

        // When/Then
        assertThatThrownBy(() -> storageService.uploadFile(emptyFile))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("empty");
    }

    @Test
    @Order(8)
    @DisplayName("Should throw exception for null file")
    void shouldThrowExceptionForNullFile() {
        // When/Then
        assertThatThrownBy(() -> storageService.uploadFile(null))
                .isInstanceOf(StorageException.class);
    }

    @Test
    @Order(9)
    @DisplayName("Should return false for non-existent file")
    void shouldReturnFalseForNonExistentFile() {
        // When
        boolean exists = storageService.fileExists("non-existent-file.pdf");

        // Then
        assertThat(exists).isFalse();
    }

    @Test
    @Order(10)
    @DisplayName("Should handle file with special characters in name")
    void shouldHandleFileWithSpecialCharacters() {
        // Given
        MultipartFile pdfFile = new MockMultipartFile(
                "document",
                "test document (1) [final].pdf",
                "application/pdf",
                SAMPLE_PDF_CONTENT
        );

        // When
        String objectKey = storageService.uploadFile(pdfFile);

        // Then
        assertThat(objectKey)
                .isNotNull()
                .doesNotContain("(")
                .doesNotContain(")")
                .doesNotContain("[")
                .doesNotContain("]")
                .doesNotContain(" ");

        assertThat(storageService.fileExists(objectKey)).isTrue();
    }

    @Test
    @Order(11)
    @DisplayName("Should upload and retrieve PDF content integrity")
    void shouldMaintainPdfContentIntegrity() throws IOException {
        // Given - Create PDF with specific content pattern
        byte[] originalContent = createPdfWithPattern("WikiTestContent12345");
        MultipartFile pdfFile = new MockMultipartFile(
                "document",
                "integrity-test.pdf",
                "application/pdf",
                originalContent
        );

        // When - Upload
        String objectKey = storageService.uploadFile(pdfFile);

        // Then - Retrieve and verify content matches exactly
        GetObjectRequest getRequest = GetObjectRequest.builder()
                .bucket(BUCKET_NAME)
                .key(objectKey)
                .build();

        ResponseInputStream<GetObjectResponse> response = s3Client.getObject(getRequest);
        byte[] retrievedContent = readAllBytes(response);

        assertThat(retrievedContent)
                .as("Retrieved content should match original content exactly")
                .isEqualTo(originalContent);
    }

    // ==================== Helper Methods ====================

    /**
     * Creates a minimal valid PDF content
     */
    private static byte[] createSamplePdfContent() {
        String pdfContent = "%PDF-1.4\n" +
                "1 0 obj\n" +
                "<< /Type /Catalog /Pages 2 0 R >>\n" +
                "endobj\n" +
                "2 0 obj\n" +
                "<< /Type /Pages /Kids [3 0 R] /Count 1 >>\n" +
                "endobj\n" +
                "3 0 obj\n" +
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] >>\n" +
                "endobj\n" +
                "xref\n" +
                "0 4\n" +
                "0000000000 65535 f \n" +
                "0000000009 00000 n \n" +
                "0000000058 00000 n \n" +
                "0000000115 00000 n \n" +
                "trailer\n" +
                "<< /Size 4 /Root 1 0 R >>\n" +
                "startxref\n" +
                "193\n" +
                "%%EOF";
        return pdfContent.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Creates a large PDF content for multipart upload testing
     */
    private static byte[] createLargePdfContent(int sizeInBytes) {
        byte[] header = createSamplePdfContent();
        byte[] content = new byte[sizeInBytes];
        System.arraycopy(header, 0, content, 0, Math.min(header.length, sizeInBytes));
        // Fill remaining with padding
        for (int i = header.length; i < sizeInBytes; i++) {
            content[i] = (byte) ('A' + (i % 26));
        }
        return content;
    }

    /**
     * Creates a PDF with a specific pattern for integrity testing
     */
    private static byte[] createPdfWithPattern(String pattern) {
        String pdfContent = "%PDF-1.4\n" +
                "% Pattern: " + pattern + "\n" +
                "1 0 obj\n" +
                "<< /Type /Catalog /Pages 2 0 R >>\n" +
                "endobj\n" +
                "2 0 obj\n" +
                "<< /Type /Pages /Kids [3 0 R] /Count 1 >>\n" +
                "endobj\n" +
                "3 0 obj\n" +
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] >>\n" +
                "endobj\n" +
                "xref\n" +
                "0 4\n" +
                "trailer\n" +
                "<< /Size 4 /Root 1 0 R >>\n" +
                "startxref\n" +
                "0\n" +
                "%%EOF";
        return pdfContent.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Read all bytes from an input stream
     */
    private byte[] readAllBytes(ResponseInputStream<?> inputStream) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] data = new byte[8192];
        int bytesRead;
        while ((bytesRead = inputStream.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, bytesRead);
        }
        return buffer.toByteArray();
    }

    /**
     * Set a private field value using reflection
     */
    private static void setFieldValue(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set field: " + fieldName, e);
        }
    }
}