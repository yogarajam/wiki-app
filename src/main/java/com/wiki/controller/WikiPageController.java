package com.wiki.controller;

import com.wiki.dto.WikiPageDTO;
import com.wiki.dto.WikiPageTreeDTO;
import com.wiki.model.WikiPage;
import com.wiki.security.SecurityValidator;
import com.wiki.service.WikiPageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * REST Controller for WikiPage operations
 */
@RestController
@RequestMapping("/api/pages")
@RequiredArgsConstructor
@Slf4j
public class WikiPageController {

    private final WikiPageService wikiPageService;
    private final SecurityValidator securityValidator;

    // ==================== Read Operations ====================

    /**
     * Get page tree structure for sidebar
     */
    @GetMapping("/tree")
    public ResponseEntity<List<WikiPageTreeDTO>> getPageTree() {
        List<WikiPage> rootPages = wikiPageService.getRootPages();
        List<WikiPageTreeDTO> tree = rootPages.stream()
                .map(this::convertToTreeDTO)
                .sorted(folderFirstComparator())
                .collect(Collectors.toList());
        return ResponseEntity.ok(tree);
    }

    /**
     * Get page by ID
     */
    @GetMapping("/{id}")
    @PreAuthorize("@securityValidator.canView(#id)")
    public ResponseEntity<WikiPageDTO> getPageById(@PathVariable Long id) {
        return wikiPageService.findById(id)
                .map(this::convertToDTO)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get page by slug
     */
    @GetMapping("/slug/{slug}")
    public ResponseEntity<WikiPageDTO> getPageBySlug(@PathVariable String slug) {
        return wikiPageService.findBySlug(slug)
                .map(this::convertToDTO)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get backlinks for a page
     */
    @GetMapping("/{id}/backlinks")
    public ResponseEntity<List<WikiPageDTO>> getBacklinks(@PathVariable Long id) {
        List<WikiPage> backlinks = wikiPageService.getBacklinks(id);
        List<WikiPageDTO> dtos = backlinks.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    /**
     * Search pages by query
     */
    @GetMapping("/search")
    public ResponseEntity<List<WikiPageDTO>> searchPages(@RequestParam("q") String query) {
        List<WikiPage> results = wikiPageService.search(query);
        List<WikiPageDTO> dtos = results.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    // ==================== Write Operations ====================

    /**
     * Create a new page
     */
    @PostMapping
    public ResponseEntity<WikiPageDTO> createPage(@RequestBody WikiPageDTO pageDTO) {
        WikiPage page = WikiPage.builder()
                .title(pageDTO.getTitle())
                .content(pageDTO.isFolder() ? null : pageDTO.getContent())
                .folder(pageDTO.isFolder())
                .build();

        WikiPage savedPage = wikiPageService.savePage(page, pageDTO.getParentId());
        return ResponseEntity.status(HttpStatus.CREATED).body(convertToDTO(savedPage));
    }

    /**
     * Update an existing page
     */
    @PutMapping("/{id}")
    @PreAuthorize("@securityValidator.canEdit(#id)")
    public ResponseEntity<WikiPageDTO> updatePage(
            @PathVariable Long id,
            @RequestBody WikiPageDTO pageDTO) {

        return wikiPageService.findById(id)
                .map(existingPage -> {
                    existingPage.setTitle(pageDTO.getTitle());
                    existingPage.setContent(pageDTO.getContent());
                    WikiPage savedPage = wikiPageService.savePage(existingPage);
                    return ResponseEntity.ok(convertToDTO(savedPage));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Move a page to a new parent
     */
    @PutMapping("/{id}/move")
    @PreAuthorize("@securityValidator.canEdit(#id)")
    public ResponseEntity<WikiPageDTO> movePage(
            @PathVariable Long id,
            @RequestBody MovePageRequest request) {

        WikiPage movedPage = wikiPageService.movePage(id, request.getParentId());
        return ResponseEntity.ok(convertToDTO(movedPage));
    }

    /**
     * Delete a page
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("@securityValidator.canDelete(#id)")
    public ResponseEntity<Void> deletePage(@PathVariable Long id) {
        if (wikiPageService.findById(id).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        wikiPageService.deletePage(id);
        return ResponseEntity.noContent().build();
    }

    // ==================== DTO Conversion ====================

    private WikiPageDTO convertToDTO(WikiPage page) {
        WikiPageDTO dto = new WikiPageDTO();
        dto.setId(page.getId());
        dto.setTitle(page.getTitle());
        dto.setSlug(page.getSlug());
        dto.setContent(page.getContent());
        dto.setVersion(page.getVersion());
        dto.setPublished(page.getPublished());
        dto.setFolder(page.getFolder());
        dto.setCreatedAt(page.getCreatedAt());
        dto.setUpdatedAt(page.getUpdatedAt());

        if (page.getParent() != null) {
            dto.setParentId(page.getParent().getId());
        }

        return dto;
    }

    private WikiPageTreeDTO convertToTreeDTO(WikiPage page) {
        WikiPageTreeDTO dto = new WikiPageTreeDTO();
        dto.setId(page.getId());
        dto.setTitle(page.getTitle());
        dto.setSlug(page.getSlug());
        dto.setFolder(page.getFolder());

        if (page.getChildren() != null && !page.getChildren().isEmpty()) {
            dto.setChildren(page.getChildren().stream()
                    .map(this::convertToTreeDTO)
                    .sorted(folderFirstComparator())
                    .collect(Collectors.toList()));
        }

        return dto;
    }

    private Comparator<WikiPageTreeDTO> folderFirstComparator() {
        return Comparator.comparing((WikiPageTreeDTO d) -> !d.isFolder())
                .thenComparing(WikiPageTreeDTO::getTitle, String.CASE_INSENSITIVE_ORDER);
    }

    // ==================== Request Classes ====================

    @lombok.Data
    public static class MovePageRequest {
        private Long parentId;
    }
}