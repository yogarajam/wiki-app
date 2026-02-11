package com.wiki.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * DTO for search results.
 */
@Data
public class SearchResultDTO {
    private String query;
    private int totalResults;
    private List<PageResult> pageResults = new ArrayList<>();
    private List<AttachmentResult> attachmentResults = new ArrayList<>();

    /**
     * Search result for a wiki page.
     */
    @Data
    public static class PageResult {
        private Long id;
        private String title;
        private String slug;
        private String snippet;
        private LocalDateTime updatedAt;
    }

    /**
     * Search result for an attachment.
     */
    @Data
    public static class AttachmentResult {
        private Long id;
        private String filename;
        private String contentType;
        private String snippet;
        private String documentTitle;
        private Long pageId;
        private String pageTitle;
    }
}