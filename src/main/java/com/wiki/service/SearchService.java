package com.wiki.service;

import com.wiki.model.Attachment;
import com.wiki.model.SearchableContent;
import com.wiki.model.SearchableContent.ExtractionStatus;
import com.wiki.model.WikiPage;
import com.wiki.repository.AttachmentRepository;
import com.wiki.repository.SearchableContentRepository;
import com.wiki.repository.WikiPageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for full-text search across wiki pages and attachments.
 * Uses Apache Tika to extract text from PDF, Word, and other documents.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SearchService {

    private final TextExtractionService textExtractionService;
    private final SearchableContentRepository searchableContentRepository;
    private final WikiPageRepository wikiPageRepository;
    private final AttachmentRepository attachmentRepository;
    private final S3Client s3Client;

    @org.springframework.beans.factory.annotation.Value("${minio.bucket:wiki-attachments}")
    private String bucketName;

    // ==================== Search Operations ====================

    /**
     * Unified search across wiki pages and attachments.
     *
     * @param query the search query
     * @return combined search results
     */
    @Transactional(readOnly = true)
    public SearchResults search(String query) {
        if (query == null || query.isBlank()) {
            return SearchResults.empty();
        }

        String normalizedQuery = query.trim();
        log.info("Performing unified search for: {}", normalizedQuery);

        // Search wiki pages (title and content)
        List<WikiPage> pageResults = wikiPageRepository.searchByTitleOrContent(normalizedQuery);

        // Search extracted attachment content
        List<SearchableContent> attachmentResults = searchableContentRepository.searchByContent(normalizedQuery);

        return SearchResults.of(pageResults, attachmentResults, normalizedQuery);
    }

    /**
     * Search only within attachments.
     *
     * @param query the search query
     * @return attachment search results
     */
    @Transactional(readOnly = true)
    public List<AttachmentSearchResult> searchAttachments(String query) {
        if (query == null || query.isBlank()) {
            return Collections.emptyList();
        }

        List<SearchableContent> results = searchableContentRepository.searchByContent(query.trim());

        return results.stream()
                .map(sc -> new AttachmentSearchResult(
                        sc.getAttachment(),
                        sc.getFilename(),
                        extractSnippet(sc.getExtractedText(), query, 200),
                        sc.getWikiPage()
                ))
                .collect(Collectors.toList());
    }

    /**
     * Search attachments within a specific wiki page.
     *
     * @param pageId the wiki page ID
     * @param query  the search query
     * @return attachment search results for the page
     */
    @Transactional(readOnly = true)
    public List<AttachmentSearchResult> searchAttachmentsInPage(Long pageId, String query) {
        if (query == null || query.isBlank()) {
            return Collections.emptyList();
        }

        List<SearchableContent> results = searchableContentRepository.searchByContentInPage(pageId, query.trim());

        return results.stream()
                .map(sc -> new AttachmentSearchResult(
                        sc.getAttachment(),
                        sc.getFilename(),
                        extractSnippet(sc.getExtractedText(), query, 200),
                        sc.getWikiPage()
                ))
                .collect(Collectors.toList());
    }

    // ==================== Indexing Operations ====================

    /**
     * Index an attachment for search (extract text and store).
     * This is called asynchronously after file upload.
     *
     * @param attachment the attachment to index
     */
    @Async
    @Transactional
    public void indexAttachment(Attachment attachment) {
        log.info("Starting async indexing for attachment: {} ({})",
                attachment.getOriginalFilename(), attachment.getId());

        // Check if already indexed
        if (searchableContentRepository.existsByAttachmentId(attachment.getId())) {
            log.info("Attachment {} already indexed, updating...", attachment.getId());
            reindexAttachment(attachment.getId());
            return;
        }

        // Create searchable content record
        SearchableContent searchableContent = SearchableContent.builder()
                .attachment(attachment)
                .wikiPage(attachment.getWikiPage())
                .filename(attachment.getOriginalFilename())
                .contentType(attachment.getContentType())
                .extractionStatus(ExtractionStatus.PROCESSING)
                .build();
        searchableContent = searchableContentRepository.save(searchableContent);

        // Check if content type is supported
        if (!textExtractionService.isSupported(attachment.getContentType())) {
            log.info("Skipping indexing for unsupported type: {}", attachment.getContentType());
            searchableContent.setExtractionStatus(ExtractionStatus.UNSUPPORTED);
            searchableContentRepository.save(searchableContent);
            return;
        }

        // Download file from MinIO and extract text
        try (InputStream inputStream = downloadFromMinio(attachment.getObjectKey())) {
            TextExtractionService.ExtractionResult result = textExtractionService.extractText(
                    inputStream,
                    attachment.getOriginalFilename(),
                    attachment.getContentType()
            );

            if (result.isSuccessful()) {
                searchableContent.setExtractedText(result.getText());
                searchableContent.setTextLength(result.getTextLength());
                searchableContent.setDocumentTitle(result.getMetadata().get("title"));
                searchableContent.setDocumentAuthor(result.getMetadata().get("author"));
                searchableContent.setExtractionStatus(ExtractionStatus.COMPLETED);

                log.info("Successfully indexed attachment: {} ({} chars)",
                        attachment.getOriginalFilename(), result.getTextLength());
            } else {
                searchableContent.setExtractionStatus(
                        result.isUnsupportedType() ? ExtractionStatus.UNSUPPORTED : ExtractionStatus.FAILED
                );
                searchableContent.setErrorMessage(result.getErrorMessage());

                log.warn("Failed to index attachment {}: {}",
                        attachment.getOriginalFilename(), result.getErrorMessage());
            }

            searchableContentRepository.save(searchableContent);

        } catch (Exception e) {
            log.error("Error indexing attachment {}: {}", attachment.getId(), e.getMessage(), e);
            searchableContent.setExtractionStatus(ExtractionStatus.FAILED);
            searchableContent.setErrorMessage("Download/extraction error: " + e.getMessage());
            searchableContentRepository.save(searchableContent);
        }
    }

    /**
     * Re-index an attachment (update extracted text).
     *
     * @param attachmentId the attachment ID to re-index
     */
    @Transactional
    public void reindexAttachment(Long attachmentId) {
        Attachment attachment = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new IllegalArgumentException("Attachment not found: " + attachmentId));

        // Delete existing searchable content
        searchableContentRepository.deleteByAttachmentId(attachmentId);

        // Re-index
        indexAttachment(attachment);
    }

    /**
     * Remove indexed content for an attachment.
     *
     * @param attachmentId the attachment ID
     */
    @Transactional
    public void removeFromIndex(Long attachmentId) {
        searchableContentRepository.deleteByAttachmentId(attachmentId);
        log.info("Removed attachment {} from search index", attachmentId);
    }

    /**
     * Reindex all attachments for a wiki page.
     *
     * @param pageId the wiki page ID
     */
    @Async
    @Transactional
    public void reindexPage(Long pageId) {
        List<Attachment> attachments = attachmentRepository.findByWikiPageId(pageId);
        log.info("Reindexing {} attachments for page {}", attachments.size(), pageId);

        for (Attachment attachment : attachments) {
            try {
                reindexAttachment(attachment.getId());
            } catch (Exception e) {
                log.error("Failed to reindex attachment {}: {}", attachment.getId(), e.getMessage());
            }
        }
    }

    /**
     * Reindex all attachments in the system.
     */
    @Async
    @Transactional
    public void reindexAll() {
        List<Attachment> allAttachments = attachmentRepository.findAll();
        log.info("Starting full reindex of {} attachments", allAttachments.size());

        int success = 0;
        int failed = 0;

        for (Attachment attachment : allAttachments) {
            try {
                // Delete existing
                searchableContentRepository.deleteByAttachmentId(attachment.getId());
                // Re-index
                indexAttachmentSync(attachment);
                success++;
            } catch (Exception e) {
                log.error("Failed to reindex attachment {}: {}", attachment.getId(), e.getMessage());
                failed++;
            }
        }

        log.info("Full reindex completed. Success: {}, Failed: {}", success, failed);
    }

    /**
     * Synchronous version of indexAttachment for batch processing.
     */
    private void indexAttachmentSync(Attachment attachment) {
        SearchableContent searchableContent = SearchableContent.builder()
                .attachment(attachment)
                .wikiPage(attachment.getWikiPage())
                .filename(attachment.getOriginalFilename())
                .contentType(attachment.getContentType())
                .extractionStatus(ExtractionStatus.PROCESSING)
                .build();
        searchableContent = searchableContentRepository.save(searchableContent);

        if (!textExtractionService.isSupported(attachment.getContentType())) {
            searchableContent.setExtractionStatus(ExtractionStatus.UNSUPPORTED);
            searchableContentRepository.save(searchableContent);
            return;
        }

        try (InputStream inputStream = downloadFromMinio(attachment.getObjectKey())) {
            TextExtractionService.ExtractionResult result = textExtractionService.extractText(
                    inputStream, attachment.getOriginalFilename(), attachment.getContentType()
            );

            if (result.isSuccessful()) {
                searchableContent.setExtractedText(result.getText());
                searchableContent.setTextLength(result.getTextLength());
                searchableContent.setDocumentTitle(result.getMetadata().get("title"));
                searchableContent.setDocumentAuthor(result.getMetadata().get("author"));
                searchableContent.setExtractionStatus(ExtractionStatus.COMPLETED);
            } else {
                searchableContent.setExtractionStatus(
                        result.isUnsupportedType() ? ExtractionStatus.UNSUPPORTED : ExtractionStatus.FAILED
                );
                searchableContent.setErrorMessage(result.getErrorMessage());
            }
        } catch (Exception e) {
            searchableContent.setExtractionStatus(ExtractionStatus.FAILED);
            searchableContent.setErrorMessage(e.getMessage());
        }

        searchableContentRepository.save(searchableContent);
    }

    // ==================== Helper Methods ====================

    /**
     * Download a file from MinIO.
     */
    private InputStream downloadFromMinio(String objectKey) {
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(objectKey)
                .build();

        ResponseInputStream<GetObjectResponse> response = s3Client.getObject(request);
        return response;
    }

    /**
     * Extract a snippet of text around the search query.
     */
    private String extractSnippet(String text, String query, int maxLength) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        String lowerText = text.toLowerCase();
        String lowerQuery = query.toLowerCase();
        int index = lowerText.indexOf(lowerQuery);

        if (index == -1) {
            // Query not found, return beginning of text
            return text.substring(0, Math.min(maxLength, text.length())) + "...";
        }

        // Calculate snippet bounds
        int snippetStart = Math.max(0, index - maxLength / 2);
        int snippetEnd = Math.min(text.length(), index + query.length() + maxLength / 2);

        StringBuilder snippet = new StringBuilder();
        if (snippetStart > 0) {
            snippet.append("...");
        }
        snippet.append(text, snippetStart, snippetEnd);
        if (snippetEnd < text.length()) {
            snippet.append("...");
        }

        return snippet.toString();
    }

    /**
     * Get search statistics.
     */
    @Transactional(readOnly = true)
    public SearchStats getSearchStats() {
        long totalDocuments = searchableContentRepository.count();
        long indexedDocuments = searchableContentRepository.countSearchableDocuments();
        long pendingDocuments = searchableContentRepository.findByExtractionStatus(ExtractionStatus.PENDING).size();
        long failedDocuments = searchableContentRepository.findByExtractionStatus(ExtractionStatus.FAILED).size();

        return new SearchStats(totalDocuments, indexedDocuments, pendingDocuments, failedDocuments);
    }

    // ==================== Result Classes ====================

    /**
     * Combined search results from pages and attachments.
     */
    public static class SearchResults {
        private final List<WikiPage> pageResults;
        private final List<SearchableContent> attachmentResults;
        private final String query;
        private final int totalResults;

        private SearchResults(List<WikiPage> pageResults, List<SearchableContent> attachmentResults, String query) {
            this.pageResults = pageResults != null ? pageResults : Collections.emptyList();
            this.attachmentResults = attachmentResults != null ? attachmentResults : Collections.emptyList();
            this.query = query;
            this.totalResults = this.pageResults.size() + this.attachmentResults.size();
        }

        public static SearchResults of(List<WikiPage> pages, List<SearchableContent> attachments, String query) {
            return new SearchResults(pages, attachments, query);
        }

        public static SearchResults empty() {
            return new SearchResults(Collections.emptyList(), Collections.emptyList(), "");
        }

        public List<WikiPage> getPageResults() {
            return pageResults;
        }

        public List<SearchableContent> getAttachmentResults() {
            return attachmentResults;
        }

        public String getQuery() {
            return query;
        }

        public int getTotalResults() {
            return totalResults;
        }

        public boolean isEmpty() {
            return totalResults == 0;
        }
    }

    /**
     * Single attachment search result with context.
     */
    public record AttachmentSearchResult(
            Attachment attachment,
            String filename,
            String snippet,
            WikiPage parentPage
    ) {}

    /**
     * Search index statistics.
     */
    public record SearchStats(
            long totalDocuments,
            long indexedDocuments,
            long pendingDocuments,
            long failedDocuments
    ) {}
}