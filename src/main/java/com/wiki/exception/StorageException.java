package com.wiki.exception;

/**
 * Custom exception for storage-related errors
 */
public class StorageException extends RuntimeException {

    private final ErrorCode errorCode;

    public enum ErrorCode {
        CONNECTION_FAILED("Unable to connect to storage server"),
        UPLOAD_FAILED("Failed to upload file"),
        DOWNLOAD_FAILED("Failed to download file"),
        DELETE_FAILED("Failed to delete file"),
        BUCKET_NOT_FOUND("Storage bucket not found"),
        FILE_NOT_FOUND("File not found"),
        INVALID_FILE("Invalid file"),
        SERVER_UNAVAILABLE("Storage server is unavailable");

        private final String message;

        ErrorCode(String message) {
            this.message = message;
        }

        public String getMessage() {
            return message;
        }
    }

    public StorageException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public StorageException(ErrorCode errorCode, String details) {
        super(errorCode.getMessage() + ": " + details);
        this.errorCode = errorCode;
    }

    public StorageException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode;
    }

    public StorageException(ErrorCode errorCode, String details, Throwable cause) {
        super(errorCode.getMessage() + ": " + details, cause);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}