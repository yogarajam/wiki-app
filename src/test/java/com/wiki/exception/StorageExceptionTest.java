package com.wiki.exception;

import com.wiki.exception.StorageException.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("StorageException Tests")
class StorageExceptionTest {

    @Nested
    @DisplayName("Constructor Tests")
    class ConstructorTests {

        @Test
        @DisplayName("Should create exception with error code only")
        void shouldCreateWithErrorCodeOnly() {
            StorageException ex = new StorageException(ErrorCode.UPLOAD_FAILED);

            assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.UPLOAD_FAILED);
            assertThat(ex.getMessage()).isEqualTo("Failed to upload file");
        }

        @Test
        @DisplayName("Should create exception with error code and details")
        void shouldCreateWithErrorCodeAndDetails() {
            StorageException ex = new StorageException(ErrorCode.FILE_NOT_FOUND, "test.pdf");

            assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.FILE_NOT_FOUND);
            assertThat(ex.getMessage()).isEqualTo("File not found: test.pdf");
        }

        @Test
        @DisplayName("Should create exception with error code and cause")
        void shouldCreateWithErrorCodeAndCause() {
            IOException cause = new IOException("IO error");
            StorageException ex = new StorageException(ErrorCode.DOWNLOAD_FAILED, cause);

            assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.DOWNLOAD_FAILED);
            assertThat(ex.getCause()).isEqualTo(cause);
        }

        @Test
        @DisplayName("Should create exception with error code, details, and cause")
        void shouldCreateWithAllParams() {
            RuntimeException cause = new RuntimeException("root cause");
            StorageException ex = new StorageException(ErrorCode.SERVER_UNAVAILABLE, "MinIO down", cause);

            assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.SERVER_UNAVAILABLE);
            assertThat(ex.getMessage()).isEqualTo("Storage server is unavailable: MinIO down");
            assertThat(ex.getCause()).isEqualTo(cause);
        }
    }

    @Nested
    @DisplayName("ErrorCode Tests")
    class ErrorCodeTests {

        @Test
        @DisplayName("Each error code should have a message")
        void eachErrorCodeShouldHaveMessage() {
            for (ErrorCode code : ErrorCode.values()) {
                assertThat(code.getMessage()).isNotNull().isNotBlank();
            }
        }

        @Test
        @DisplayName("Should have all expected error codes")
        void shouldHaveAllExpectedErrorCodes() {
            assertThat(ErrorCode.values()).containsExactlyInAnyOrder(
                    ErrorCode.CONNECTION_FAILED,
                    ErrorCode.UPLOAD_FAILED,
                    ErrorCode.DOWNLOAD_FAILED,
                    ErrorCode.DELETE_FAILED,
                    ErrorCode.BUCKET_NOT_FOUND,
                    ErrorCode.FILE_NOT_FOUND,
                    ErrorCode.INVALID_FILE,
                    ErrorCode.SERVER_UNAVAILABLE
            );
        }
    }

    // Using a simple IOException stand-in since we only need it for testing
    private static class IOException extends Exception {
        IOException(String message) {
            super(message);
        }
    }
}