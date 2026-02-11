package com.wiki.controller;

import com.wiki.dto.SearchResultDTO;
import com.wiki.model.SearchableContent;
import com.wiki.model.WikiPage;
import com.wiki.service.SearchService;
import com.wiki.service.SearchService.AttachmentSearchResult;
import com.wiki.service.SearchService.SearchResults;
import com.wiki.service.SearchService.SearchStats;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * REST Controller for search operations.
 */
@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
@Slf4j
public class SearchController {

    private final SearchService searchService;

    /**
     * Unified search across pages and attachments.
     */
    @GetMapping
    public ResponseEntity<SearchResultDTO> search(@RequestParam("q") String query) {
        log.info("Search request for: {}", query);

        SearchResults results = searchService.search(query);

        SearchResultDTO dto = new SearchResultDTO();
        dto.setQuery(results.getQuery());
        dto.setTotalResults(results.getTotalResults());

        // Convert page results
        dto.setPageResults(results.getPageResults().stream()
                .map(this::convertPageToResult)
                .collect(Collectors.toList()));

        // Convert attachment results
        dto.setAttachmentResults(results.getAttachmentResults().stream()
                .map(this::convertAttachmentToResult)
                .collect(Collectors.toList()));

        return ResponseEntity.ok(dto);
    }

    /**
     * Search only in attachments.
     */
    @GetMapping("/attachments")
    public ResponseEntity<List<SearchResultDTO.AttachmentResult>> searchAttachments(
            @RequestParam("q") String query) {

        List<AttachmentSearchResult> results = searchService.searchAttachments(query);

        List<SearchResultDTO.AttachmentResult> dtos = results.stream()
                .map(this::convertToAttachmentResult)
                .collect(Collectors.toList());

        return ResponseEntity.ok(dtos);
    }

    /**
     * Search attachments within a specific page.
     */
    @GetMapping("/page/{pageId}/attachments")
    public ResponseEntity<List<SearchResultDTO.AttachmentResult>> searchAttachmentsInPage(
            @PathVariable Long pageId,
            @RequestParam("q") String query) {

        List<AttachmentSearchResult> results = searchService.searchAttachmentsInPage(pageId, query);

        List<SearchResultDTO.AttachmentResult> dtos = results.stream()
                .map(this::convertToAttachmentResult)
                .collect(Collectors.toList());

        return ResponseEntity.ok(dtos);
    }

    /**
     * Get search statistics (admin only).
     */
    @GetMapping("/stats")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SearchStats> getStats() {
        return ResponseEntity.ok(searchService.getSearchStats());
    }

    /**
     * Trigger reindex of all attachments (admin only).
     */
    @PostMapping("/reindex")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> reindexAll() {
        log.info("Triggering full reindex");
        searchService.reindexAll();
        return ResponseEntity.ok("Reindex started");
    }

    /**
     * Reindex a specific page's attachments (admin only).
     */
    @PostMapping("/reindex/page/{pageId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> reindexPage(@PathVariable Long pageId) {
        log.info("Triggering reindex for page {}", pageId);
        searchService.reindexPage(pageId);
        return ResponseEntity.ok("Page reindex started");
    }

    // ==================== Helper Methods ====================

    private SearchResultDTO.PageResult convertPageToResult(WikiPage page) {
        SearchResultDTO.PageResult result = new SearchResultDTO.PageResult();
        result.setId(page.getId());
        result.setTitle(page.getTitle());
        result.setSlug(page.getSlug());
        result.setSnippet(extractSnippet(page.getContent(), 200));
        result.setUpdatedAt(page.getUpdatedAt());
        return result;
    }

    private SearchResultDTO.AttachmentResult convertAttachmentToResult(SearchableContent sc) {
        SearchResultDTO.AttachmentResult result = new SearchResultDTO.AttachmentResult();
        result.setId(sc.getAttachment() != null ? sc.getAttachment().getId() : null);
        result.setFilename(sc.getFilename());
        result.setContentType(sc.getContentType());
        result.setSnippet(extractSnippet(sc.getExtractedText(), 200));
        result.setDocumentTitle(sc.getDocumentTitle());
        if (sc.getWikiPage() != null) {
            result.setPageId(sc.getWikiPage().getId());
            result.setPageTitle(sc.getWikiPage().getTitle());
        }
        return result;
    }

    private SearchResultDTO.AttachmentResult convertToAttachmentResult(AttachmentSearchResult asr) {
        SearchResultDTO.AttachmentResult result = new SearchResultDTO.AttachmentResult();
        result.setId(asr.attachment() != null ? asr.attachment().getId() : null);
        result.setFilename(asr.filename());
        result.setSnippet(asr.snippet());
        if (asr.parentPage() != null) {
            result.setPageId(asr.parentPage().getId());
            result.setPageTitle(asr.parentPage().getTitle());
        }
        return result;
    }

    private String extractSnippet(String content, int maxLength) {
        if (content == null || content.isEmpty()) {
            return "";
        }
        if (content.length() <= maxLength) {
            return content;
        }
        return content.substring(0, maxLength) + "...";
    }
}