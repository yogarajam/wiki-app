package com.wiki.service;

import com.wiki.exception.StorageException;
import com.wiki.exception.StorageException.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ServiceClientConfiguration;
import software.amazon.awssdk.services.s3.model.*;

import java.io.IOException;
import java.net.URI;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("MinioStorageService Unit Tests")
class MinioStorageServiceUnitTest {

    @Mock
    private S3Client s3Client;

    private MinioStorageService storageService;

    @BeforeEach
    void setUp() {
        storageService = new MinioStorageService(s3Client);
        setField(storageService, "bucketName", "test-bucket");
        setField(storageService, "maxRetries", 1);
    }

    // ==================== File Validation Tests ====================

    @Nested
    @DisplayName("File Validation Tests")
    class FileValidationTests {

        @Test
        @DisplayName("Should throw INVALID_FILE for null file")
        void shouldThrowForNullFile() {
            assertThatThrownBy(() -> storageService.uploadFile(null))
                    .isInstanceOf(StorageException.class)
                    .satisfies(ex -> {
                        StorageException se = (StorageException) ex;
                        assertThat(se.getErrorCode()).isEqualTo(ErrorCode.INVALID_FILE);
                    });
        }

        @Test
        @DisplayName("Should throw INVALID_FILE for empty file")
        void shouldThrowForEmptyFile() {
            MultipartFile file = new MockMultipartFile("file", "test.pdf", "application/pdf", new byte[0]);

            assertThatThrownBy(() -> storageService.uploadFile(file))
                    .isInstanceOf(StorageException.class)
                    .satisfies(ex -> {
                        StorageException se = (StorageException) ex;
                        assertThat(se.getErrorCode()).isEqualTo(ErrorCode.INVALID_FILE);
                    });
        }

        @Test
        @DisplayName("Should throw INVALID_FILE for file without name")
        void shouldThrowForFileWithoutName() {
            MultipartFile file = new MockMultipartFile("file", "", "application/pdf", "content".getBytes());

            assertThatThrownBy(() -> storageService.uploadFile(file))
                    .isInstanceOf(StorageException.class)
                    .satisfies(ex -> {
                        StorageException se = (StorageException) ex;
                        assertThat(se.getErrorCode()).isEqualTo(ErrorCode.INVALID_FILE);
                    });
        }

        @Test
        @DisplayName("Should throw INVALID_FILE for file with null name")
        void shouldThrowForFileWithNullName() {
            MultipartFile file = new MockMultipartFile("file", null, "application/pdf", "content".getBytes());

            assertThatThrownBy(() -> storageService.uploadFile(file))
                    .isInstanceOf(StorageException.class)
                    .satisfies(ex -> {
                        StorageException se = (StorageException) ex;
                        assertThat(se.getErrorCode()).isEqualTo(ErrorCode.INVALID_FILE);
                    });
        }
    }

    // ==================== Upload Tests ====================

    @Nested
    @DisplayName("Upload Tests")
    class UploadTests {

        @Test
        @DisplayName("Should upload file successfully via simple upload")
        void shouldUploadFileSuccessfully() {
            MultipartFile file = new MockMultipartFile(
                    "file", "test.pdf", "application/pdf", "pdf content".getBytes());

            // Mock bucket check
            when(s3Client.headBucket(any(HeadBucketRequest.class)))
                    .thenReturn(HeadBucketResponse.builder().build());

            // Mock putObject
            when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                    .thenReturn(PutObjectResponse.builder().build());

            String objectKey = storageService.uploadFile(file);

            assertThat(objectKey).isNotNull().contains("test.pdf");
            verify(s3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));
        }

        @Test
        @DisplayName("Should upload file with path prefix")
        void shouldUploadFileWithPathPrefix() {
            MultipartFile file = new MockMultipartFile(
                    "file", "doc.pdf", "application/pdf", "content".getBytes());

            when(s3Client.headBucket(any(HeadBucketRequest.class)))
                    .thenReturn(HeadBucketResponse.builder().build());
            when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                    .thenReturn(PutObjectResponse.builder().build());

            String objectKey = storageService.uploadFile(file, "pages/1/attachments");

            assertThat(objectKey).startsWith("pages/1/attachments/");
            assertThat(objectKey).contains("doc.pdf");
        }

        @Test
        @DisplayName("Should upload file with trailing slash path prefix")
        void shouldUploadFileWithTrailingSlashPrefix() {
            MultipartFile file = new MockMultipartFile(
                    "file", "test.pdf", "application/pdf", "content".getBytes());

            when(s3Client.headBucket(any(HeadBucketRequest.class)))
                    .thenReturn(HeadBucketResponse.builder().build());
            when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                    .thenReturn(PutObjectResponse.builder().build());

            String objectKey = storageService.uploadFile(file, "pages/1/");

            assertThat(objectKey).startsWith("pages/1/");
        }

        @Test
        @DisplayName("Should sanitize filename with special characters")
        void shouldSanitizeFilename() {
            MultipartFile file = new MockMultipartFile(
                    "file", "my file (1) [final].pdf", "application/pdf", "content".getBytes());

            when(s3Client.headBucket(any(HeadBucketRequest.class)))
                    .thenReturn(HeadBucketResponse.builder().build());
            when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                    .thenReturn(PutObjectResponse.builder().build());

            String objectKey = storageService.uploadFile(file);

            assertThat(objectKey).doesNotContain("(").doesNotContain(")").doesNotContain(" ");
            assertThat(objectKey).contains("my_file__1___final_.pdf");
        }

        @Test
        @DisplayName("Upload with null pathPrefix should work without prefix")
        void shouldUploadWithNullPathPrefix() {
            MultipartFile file = new MockMultipartFile(
                    "file", "test.pdf", "application/pdf", "content".getBytes());

            when(s3Client.headBucket(any(HeadBucketRequest.class)))
                    .thenReturn(HeadBucketResponse.builder().build());
            when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                    .thenReturn(PutObjectResponse.builder().build());

            String objectKey = storageService.uploadFile(file, null);

            assertThat(objectKey).doesNotContain("/");
        }
    }

    // ==================== Bucket Management Tests ====================

    @Nested
    @DisplayName("Bucket Management Tests")
    class BucketManagementTests {

        @Test
        @DisplayName("Should create bucket if it does not exist")
        void shouldCreateBucketIfNotExists() {
            MultipartFile file = new MockMultipartFile(
                    "file", "test.pdf", "application/pdf", "content".getBytes());

            when(s3Client.headBucket(any(HeadBucketRequest.class)))
                    .thenThrow(NoSuchBucketException.builder().message("No bucket").build());
            when(s3Client.createBucket(any(CreateBucketRequest.class)))
                    .thenReturn(CreateBucketResponse.builder().build());
            when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                    .thenReturn(PutObjectResponse.builder().build());

            storageService.uploadFile(file);

            verify(s3Client).createBucket(any(CreateBucketRequest.class));
        }
    }

    // ==================== Delete File Tests ====================

    @Nested
    @DisplayName("Delete File Tests")
    class DeleteFileTests {

        @Test
        @DisplayName("Should delete file successfully")
        void shouldDeleteFileSuccessfully() {
            when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                    .thenReturn(DeleteObjectResponse.builder().build());

            storageService.deleteFile("some-key");

            verify(s3Client).deleteObject(any(DeleteObjectRequest.class));
        }

        @Test
        @DisplayName("Should throw StorageException on delete failure")
        void shouldThrowOnDeleteFailure() {
            when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                    .thenThrow(SdkClientException.builder().message("delete failed").build());

            assertThatThrownBy(() -> storageService.deleteFile("some-key"))
                    .isInstanceOf(StorageException.class);
        }
    }

    // ==================== File Exists Tests ====================

    @Nested
    @DisplayName("File Exists Tests")
    class FileExistsTests {

        @Test
        @DisplayName("Should return true when file exists")
        void shouldReturnTrueWhenFileExists() {
            when(s3Client.headObject(any(HeadObjectRequest.class)))
                    .thenReturn(HeadObjectResponse.builder().build());

            assertThat(storageService.fileExists("existing-key")).isTrue();
        }

        @Test
        @DisplayName("Should return false when file does not exist")
        void shouldReturnFalseWhenFileNotExists() {
            when(s3Client.headObject(any(HeadObjectRequest.class)))
                    .thenThrow(NoSuchKeyException.builder().message("not found").build());

            assertThat(storageService.fileExists("missing-key")).isFalse();
        }

        @Test
        @DisplayName("Should throw StorageException on connection error")
        void shouldThrowOnConnectionError() {
            when(s3Client.headObject(any(HeadObjectRequest.class)))
                    .thenThrow(SdkClientException.builder().message("connection refused").build());

            assertThatThrownBy(() -> storageService.fileExists("some-key"))
                    .isInstanceOf(StorageException.class);
        }
    }

    // ==================== Get File URL Tests ====================

    @Nested
    @DisplayName("File URL Tests")
    class FileUrlTests {

        @Test
        @DisplayName("Should return file URL with bucket and key")
        void shouldReturnFileUrl() {
            S3ServiceClientConfiguration config = mock(S3ServiceClientConfiguration.class);
            when(s3Client.serviceClientConfiguration()).thenReturn(config);
            when(config.endpointOverride()).thenReturn(Optional.of(URI.create("http://localhost:9000")));

            String url = storageService.getFileUrl("my-key");

            assertThat(url).isEqualTo("http://localhost:9000/test-bucket/my-key");
        }

        @Test
        @DisplayName("Should handle missing endpoint override")
        void shouldHandleMissingEndpoint() {
            S3ServiceClientConfiguration config = mock(S3ServiceClientConfiguration.class);
            when(s3Client.serviceClientConfiguration()).thenReturn(config);
            when(config.endpointOverride()).thenReturn(Optional.empty());

            String url = storageService.getFileUrl("my-key");

            assertThat(url).isEqualTo("/test-bucket/my-key");
        }

        @Test
        @DisplayName("getPresignedUrl should delegate to getFileUrl")
        void presignedUrlShouldDelegateToGetFileUrl() {
            S3ServiceClientConfiguration config = mock(S3ServiceClientConfiguration.class);
            when(s3Client.serviceClientConfiguration()).thenReturn(config);
            when(config.endpointOverride()).thenReturn(Optional.of(URI.create("http://localhost:9000")));

            String url = storageService.getPresignedUrl("my-key", 3600);

            assertThat(url).isEqualTo("http://localhost:9000/test-bucket/my-key");
        }
    }

    // ==================== Error Handling Tests ====================

    @Nested
    @DisplayName("Error Handling Tests")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Should wrap S3 exception as StorageException on upload")
        void shouldWrapS3ExceptionOnUpload() {
            MultipartFile file = new MockMultipartFile(
                    "file", "test.pdf", "application/pdf", "content".getBytes());

            when(s3Client.headBucket(any(HeadBucketRequest.class)))
                    .thenReturn(HeadBucketResponse.builder().build());
            when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                    .thenThrow(SdkClientException.builder().message("S3 error").build());

            assertThatThrownBy(() -> storageService.uploadFile(file))
                    .isInstanceOf(StorageException.class);
        }

        @Test
        @DisplayName("Should wrap IOException as StorageException")
        void shouldWrapIOExceptionOnUpload() throws IOException {
            MultipartFile file = mock(MultipartFile.class);
            when(file.isEmpty()).thenReturn(false);
            when(file.getOriginalFilename()).thenReturn("test.pdf");
            when(file.getSize()).thenReturn(100L);
            when(file.getContentType()).thenReturn("application/pdf");
            when(file.getInputStream()).thenThrow(new IOException("stream error"));

            when(s3Client.headBucket(any(HeadBucketRequest.class)))
                    .thenReturn(HeadBucketResponse.builder().build());

            assertThatThrownBy(() -> storageService.uploadFile(file))
                    .isInstanceOf(StorageException.class)
                    .hasMessageContaining("Failed to read file");
        }

        @Test
        @DisplayName("Should throw StorageException when bucket check fails with connection error")
        void shouldThrowOnBucketCheckConnectionError() {
            MultipartFile file = new MockMultipartFile(
                    "file", "test.pdf", "application/pdf", "content".getBytes());

            when(s3Client.headBucket(any(HeadBucketRequest.class)))
                    .thenThrow(SdkClientException.builder().message("Connection refused").build());

            assertThatThrownBy(() -> storageService.uploadFile(file))
                    .isInstanceOf(StorageException.class);
        }
    }

    // ==================== Helper ====================

    private static void setField(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set field: " + fieldName, e);
        }
    }
}