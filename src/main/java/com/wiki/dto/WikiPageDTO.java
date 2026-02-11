package com.wiki.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * DTO for WikiPage data transfer
 */
@Data
public class WikiPageDTO {
    private Long id;
    private String title;
    private String slug;
    private String content;
    private Integer version;
    private boolean published;
    private boolean folder;
    private Long parentId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}