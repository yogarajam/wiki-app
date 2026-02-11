package com.wiki.dto;

import lombok.Data;

/**
 * DTO for file upload response
 */
@Data
public class UploadResponseDTO {
    private boolean success;
    private String url;
    private String objectKey;
    private String originalFilename;
    private String contentType;
    private Long fileSize;
    private Long attachmentId;
    private String error;
}