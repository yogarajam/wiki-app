package com.wiki.controller;

import com.wiki.exception.StorageException;
import com.wiki.exception.StorageException.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Global Exception Handler Tests")
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Nested
    @DisplayName("IllegalArgumentException Tests")
    class IllegalArgumentExceptionTests {

        @Test
        @DisplayName("Should return 400 BAD_REQUEST")
        void shouldReturn400() {
            ResponseEntity<Map<String, Object>> response =
                    handler.handleIllegalArgument(new IllegalArgumentException("Invalid input"));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody().get("status")).isEqualTo(400);
            assertThat(response.getBody().get("message")).isEqualTo("Invalid input");
            assertThat(response.getBody().get("error")).isEqualTo("Bad Request");
            assertThat(response.getBody()).containsKey("timestamp");
        }
    }

    @Nested
    @DisplayName("StorageException Tests")
    class StorageExceptionTests {

        @Test
        @DisplayName("Should return 503 for SERVER_UNAVAILABLE")
        void shouldReturn503ForServerUnavailable() {
            ResponseEntity<Map<String, Object>> response =
                    handler.handleStorageException(new StorageException(ErrorCode.SERVER_UNAVAILABLE));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        }

        @Test
        @DisplayName("Should return 503 for CONNECTION_FAILED")
        void shouldReturn503ForConnectionFailed() {
            ResponseEntity<Map<String, Object>> response =
                    handler.handleStorageException(new StorageException(ErrorCode.CONNECTION_FAILED));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        }

        @Test
        @DisplayName("Should return 404 for FILE_NOT_FOUND")
        void shouldReturn404ForFileNotFound() {
            ResponseEntity<Map<String, Object>> response =
                    handler.handleStorageException(new StorageException(ErrorCode.FILE_NOT_FOUND));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("Should return 404 for BUCKET_NOT_FOUND")
        void shouldReturn404ForBucketNotFound() {
            ResponseEntity<Map<String, Object>> response =
                    handler.handleStorageException(new StorageException(ErrorCode.BUCKET_NOT_FOUND));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("Should return 400 for INVALID_FILE")
        void shouldReturn400ForInvalidFile() {
            ResponseEntity<Map<String, Object>> response =
                    handler.handleStorageException(new StorageException(ErrorCode.INVALID_FILE));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("Should return 500 for UPLOAD_FAILED")
        void shouldReturn500ForUploadFailed() {
            ResponseEntity<Map<String, Object>> response =
                    handler.handleStorageException(new StorageException(ErrorCode.UPLOAD_FAILED));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Nested
    @DisplayName("Generic Exception Tests")
    class GenericExceptionTests {

        @Test
        @DisplayName("Should return 500 INTERNAL_SERVER_ERROR")
        void shouldReturn500() {
            ResponseEntity<Map<String, Object>> response =
                    handler.handleGenericException(new RuntimeException("Something broke"));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
            assertThat(response.getBody().get("message")).isEqualTo("An unexpected error occurred");
        }
    }
}