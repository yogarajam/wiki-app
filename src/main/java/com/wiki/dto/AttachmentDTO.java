package com.wiki.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * DTO for Attachment data transfer
 */
@Data
public class AttachmentDTO {
    private Long id;
    private String originalFilename;
    private String objectKey;
    private String contentType;
    private Long fileSize;
    private String url;
    private LocalDateTime uploadedAt;
}