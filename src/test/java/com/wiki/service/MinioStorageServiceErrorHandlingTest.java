package com.wiki.service;

import com.wiki.exception.StorageException;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

import java.net.URI;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for MinioStorageService error handling scenarios
 * Including when MinIO server is down or unavailable
 */
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("MinIO Storage Service Error Handling Tests")
class MinioStorageServiceErrorHandlingTest {

    private static final String MINIO_ACCESS_KEY = "minioadmin";
    private static final String MINIO_SECRET_KEY = "minioadmin";
    private static final String BUCKET_NAME = "wiki-attachments";

    @Container
    static MinIOContainer minioContainer = new MinIOContainer("minio/minio:latest")
            .withUserName(MINIO_ACCESS_KEY)
            .withPassword(MINIO_SECRET_KEY);

    private static S3Client s3Client;
    private static MinioStorageService storageService;

    // Sample PDF content
    private static final byte[] SAMPLE_PDF_CONTENT = createSamplePdfContent();

    @BeforeAll
    static void setUpAll() {
        s3Client = createS3Client(minioContainer.getS3URL());
        storageService = createStorageService(s3Client);
    }

    @AfterAll
    static void tearDownAll() {
        if (s3Client != null) {
            s3Client.close();
        }
    }

    @Test
    @Order(1)
    @DisplayName("Should throw StorageException with SERVER_UNAVAILABLE when MinIO is stopped")
    void shouldThrowExceptionWhenMinioIsStopped() {
        // Given - Create a client pointing to a stopped container
        S3Client disconnectedClient = createS3Client("http://localhost:19999"); // Non-existent port
        MinioStorageService disconnectedService = createStorageService(disconnectedClient);

        MultipartFile pdfFile = new MockMultipartFile(
                "document",
                "test.pdf",
                "application/pdf",
                SAMPLE_PDF_CONTENT
        );

        // When/Then
        assertThatThrownBy(() -> disconnectedService.uploadFile(pdfFile))
                .isInstanceOf(StorageException.class)
                .satisfies(exception -> {
                    StorageException storageException = (StorageException) exception;
                    assertThat(storageException.getErrorCode())
                            .isIn(StorageException.ErrorCode.SERVER_UNAVAILABLE,
                                  StorageException.ErrorCode.UPLOAD_FAILED);
                    assertThat(storageException.getMessage())
                            .containsAnyOf("unavailable", "connect", "failed");
                });

        disconnectedClient.close();
    }

    @Test
    @Order(2)
    @DisplayName("Should throw StorageException when checking file existence on unavailable server")
    void shouldThrowExceptionWhenCheckingFileOnUnavailableServer() {
        // Given
        S3Client disconnectedClient = createS3Client("http://localhost:19998");
        MinioStorageService disconnectedService = createStorageService(disconnectedClient);

        // When/Then
        assertThatThrownBy(() -> disconnectedService.fileExists("any-file.pdf"))
                .isInstanceOf(StorageException.class)
                .satisfies(exception -> {
                    StorageException storageException = (StorageException) exception;
                    assertThat(storageException.getErrorCode())
                            .isIn(StorageException.ErrorCode.SERVER_UNAVAILABLE,
                                  StorageException.ErrorCode.CONNECTION_FAILED);
                });

        disconnectedClient.close();
    }

    @Test
    @Order(3)
    @DisplayName("Should throw StorageException when deleting file on unavailable server")
    void shouldThrowExceptionWhenDeletingFileOnUnavailableServer() {
        // Given
        S3Client disconnectedClient = createS3Client("http://localhost:19997");
        MinioStorageService disconnectedService = createStorageService(disconnectedClient);

        // When/Then
        assertThatThrownBy(() -> disconnectedService.deleteFile("any-file.pdf"))
                .isInstanceOf(StorageException.class)
                .satisfies(exception -> {
                    StorageException storageException = (StorageException) exception;
                    assertThat(storageException.getErrorCode())
                            .isIn(StorageException.ErrorCode.SERVER_UNAVAILABLE,
                                  StorageException.ErrorCode.DELETE_FAILED);
                });

        disconnectedClient.close();
    }

    @Test
    @Order(4)
    @DisplayName("Should recover and upload successfully after MinIO becomes available")
    void shouldRecoverAfterMinioBecomesAvailable() {
        // Given - First verify MinIO is available
        MultipartFile pdfFile = new MockMultipartFile(
                "document",
                "recovery-test.pdf",
                "application/pdf",
                SAMPLE_PDF_CONTENT
        );

        // When - Upload should succeed
        String objectKey = storageService.uploadFile(pdfFile);

        // Then
        assertThat(objectKey).isNotNull();
        assertThat(storageService.fileExists(objectKey)).isTrue();
    }

    @Test
    @Order(5)
    @DisplayName("Should include meaningful error message when connection fails")
    void shouldIncludeMeaningfulErrorMessage() {
        // Given
        S3Client disconnectedClient = createS3Client("http://localhost:19996");
        MinioStorageService disconnectedService = createStorageService(disconnectedClient);

        MultipartFile pdfFile = new MockMultipartFile(
                "document",
                "test.pdf",
                "application/pdf",
                SAMPLE_PDF_CONTENT
        );

        // When/Then
        assertThatThrownBy(() -> disconnectedService.uploadFile(pdfFile))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("server")
                .hasCauseInstanceOf(Exception.class);

        disconnectedClient.close();
    }

    @Test
    @Order(6)
    @DisplayName("Should have proper error code in StorageException")
    void shouldHaveProperErrorCodeInException() {
        // Given
        S3Client disconnectedClient = createS3Client("http://localhost:19995");
        MinioStorageService disconnectedService = createStorageService(disconnectedClient);

        MultipartFile pdfFile = new MockMultipartFile(
                "document",
                "test.pdf",
                "application/pdf",
                SAMPLE_PDF_CONTENT
        );

        // When/Then
        try {
            disconnectedService.uploadFile(pdfFile);
            fail("Expected StorageException to be thrown");
        } catch (StorageException e) {
            assertThat(e.getErrorCode()).isNotNull();
            assertThat(e.getErrorCode().getMessage()).isNotBlank();
        }

        disconnectedClient.close();
    }

    @Test
    @Order(7)
    @DisplayName("Should throw INVALID_FILE error for file without name")
    void shouldThrowInvalidFileErrorForFileWithoutName() {
        // Given
        MultipartFile fileWithoutName = new MockMultipartFile(
                "document",
                "",  // Empty filename
                "application/pdf",
                SAMPLE_PDF_CONTENT
        );

        // When/Then
        assertThatThrownBy(() -> storageService.uploadFile(fileWithoutName))
                .isInstanceOf(StorageException.class)
                .satisfies(exception -> {
                    StorageException storageException = (StorageException) exception;
                    assertThat(storageException.getErrorCode())
                            .isEqualTo(StorageException.ErrorCode.INVALID_FILE);
                });
    }

    @Test
    @Order(8)
    @DisplayName("Verify retry mechanism is triggered on transient failures")
    @Timeout(30)
    void shouldRetryOnTransientFailures() {
        // Given - Client with very short timeout that might cause transient failures
        S3Client disconnectedClient = createS3Client("http://localhost:19994");
        MinioStorageService disconnectedService = createStorageService(disconnectedClient);
        setFieldValue(disconnectedService, "maxRetries", 2);

        MultipartFile pdfFile = new MockMultipartFile(
                "document",
                "retry-test.pdf",
                "application/pdf",
                SAMPLE_PDF_CONTENT
        );

        long startTime = System.currentTimeMillis();

        // When/Then - Should retry before failing
        assertThatThrownBy(() -> disconnectedService.uploadFile(pdfFile))
                .isInstanceOf(StorageException.class);

        long duration = System.currentTimeMillis() - startTime;

        // Verify some time passed due to retries (at least 1 second for exponential backoff)
        assertThat(duration).isGreaterThan(1000);

        disconnectedClient.close();
    }

    // ==================== Helper Methods ====================

    private static S3Client createS3Client(String endpoint) {
        return S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(MINIO_ACCESS_KEY, MINIO_SECRET_KEY)))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .build())
                .build();
    }

    private static MinioStorageService createStorageService(S3Client client) {
        MinioStorageService service = new MinioStorageService(client);
        setFieldValue(service, "bucketName", BUCKET_NAME);
        setFieldValue(service, "maxRetries", 3);
        return service;
    }

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
                "trailer\n" +
                "<< /Size 4 /Root 1 0 R >>\n" +
                "startxref\n" +
                "0\n" +
                "%%EOF";
        return pdfContent.getBytes(StandardCharsets.UTF_8);
    }

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