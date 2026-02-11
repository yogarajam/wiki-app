package com.wiki.service;

import com.wiki.exception.StorageException;
import com.wiki.model.WikiPage;
import com.wiki.repository.WikiPageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Service for managing WikiPages
 * Handles page CRUD, hierarchy management, and backlink parsing
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class WikiPageService {

    private final WikiPageRepository wikiPageRepository;

    // Pattern to match internal wiki links: [[PageName]] or [[PageName|Display Text]]
    private static final Pattern WIKI_LINK_PATTERN = Pattern.compile("\\[\\[([^\\]|]+)(?:\\|[^\\]]*)?\\]\\]");

    // ==================== Save Operations ====================

    /**
     * Save a wiki page, parsing content for internal links and updating backlinks
     *
     * @param page the wiki page to save
     * @return the saved wiki page
     */
    @Transactional
    public WikiPage savePage(WikiPage page) {
        validatePage(page);

        // Generate slug if not provided
        if (page.getSlug() == null || page.getSlug().isBlank()) {
            page.setSlug(generateUniqueSlug(page.getTitle()));
        }

        // If updating existing page, increment version
        if (page.getId() != null) {
            page.setVersion(page.getVersion() + 1);
        }

        // Folders have no content or backlinks
        if (Boolean.TRUE.equals(page.getFolder())) {
            page.setContent(null);
            page.clearLinksTo();
        } else {
            // Parse content for internal links and update backlinks
            updateBacklinks(page);
        }

        WikiPage savedPage = wikiPageRepository.save(page);
        log.info("Saved wiki page: {} (id: {})", savedPage.getTitle(), savedPage.getId());

        return savedPage;
    }

    /**
     * Save a page with a specific parent (for hierarchy)
     *
     * @param page     the wiki page to save
     * @param parentId the ID of the parent page (null for root)
     * @return the saved wiki page
     */
    @Transactional
    public WikiPage savePage(WikiPage page, Long parentId) {
        if (parentId != null) {
            WikiPage parent = wikiPageRepository.findById(parentId)
                    .orElseThrow(() -> new IllegalArgumentException("Parent page not found: " + parentId));
            page.setParent(parent);
        } else {
            page.setParent(null);
        }
        return savePage(page);
    }

    // ==================== Backlink Management ====================

    /**
     * Parse content for [[PageName]] links and update the linksTo relationship
     *
     * @param page the wiki page to update backlinks for
     */
    private void updateBacklinks(WikiPage page) {
        String content = page.getContent();
        if (content == null || content.isBlank()) {
            page.clearLinksTo();
            return;
        }

        // Extract all page names from [[PageName]] or [[PageName|Display Text]] syntax
        Set<String> linkedPageNames = extractLinkedPageNames(content);
        log.debug("Found {} internal links in page '{}': {}",
                linkedPageNames.size(), page.getTitle(), linkedPageNames);

        // Clear existing links
        page.clearLinksTo();

        // Find and link to existing pages
        for (String pageName : linkedPageNames) {
            // Try to find by title first, then by slug
            Optional<WikiPage> targetPage = findPageByTitleOrSlug(pageName);

            if (targetPage.isPresent()) {
                WikiPage target = targetPage.get();
                // Avoid self-referencing
                if (!target.equals(page)) {
                    page.addLinkTo(target);
                    log.debug("Added link from '{}' to '{}'", page.getTitle(), target.getTitle());
                }
            } else {
                log.debug("Linked page not found: '{}' (will be a broken link)", pageName);
            }
        }
    }

    /**
     * Extract page names from wiki link syntax [[PageName]] or [[PageName|Display Text]]
     *
     * @param content the content to parse
     * @return set of page names found in the content
     */
    public Set<String> extractLinkedPageNames(String content) {
        if (content == null) {
            return Collections.emptySet();
        }
        Set<String> pageNames = new HashSet<>();
        Matcher matcher = WIKI_LINK_PATTERN.matcher(content);

        while (matcher.find()) {
            String pageName = matcher.group(1).trim();
            if (!pageName.isEmpty()) {
                pageNames.add(pageName);
            }
        }

        return pageNames;
    }

    /**
     * Find broken links in page content (links to non-existent pages)
     *
     * @param page the wiki page to check
     * @return list of page names that don't exist
     */
    public List<String> findBrokenLinks(WikiPage page) {
        if (page.getContent() == null) {
            return Collections.emptyList();
        }

        Set<String> linkedPageNames = extractLinkedPageNames(page.getContent());
        List<String> brokenLinks = new ArrayList<>();

        for (String pageName : linkedPageNames) {
            if (findPageByTitleOrSlug(pageName).isEmpty()) {
                brokenLinks.add(pageName);
            }
        }

        return brokenLinks;
    }

    /**
     * Get all pages that link to a specific page (backlinks)
     *
     * @param pageId the ID of the page to find backlinks for
     * @return list of pages that link to this page
     */
    @Transactional(readOnly = true)
    public List<WikiPage> getBacklinks(Long pageId) {
        return wikiPageRepository.findPagesLinkingTo(pageId);
    }

    /**
     * Get all pages that a specific page links to
     *
     * @param pageId the ID of the page
     * @return set of pages this page links to
     */
    @Transactional(readOnly = true)
    public Set<WikiPage> getOutgoingLinks(Long pageId) {
        return wikiPageRepository.findPagesLinkedFrom(pageId);
    }

    // ==================== Read Operations ====================

    /**
     * Find a page by ID
     */
    @Transactional(readOnly = true)
    public Optional<WikiPage> findById(Long id) {
        return wikiPageRepository.findById(id);
    }

    /**
     * Find a page by slug
     */
    @Transactional(readOnly = true)
    public Optional<WikiPage> findBySlug(String slug) {
        return wikiPageRepository.findBySlug(slug);
    }

    /**
     * Find a page by title
     */
    @Transactional(readOnly = true)
    public Optional<WikiPage> findByTitle(String title) {
        return wikiPageRepository.findByTitle(title);
    }

    /**
     * Find a page by title or slug
     */
    @Transactional(readOnly = true)
    public Optional<WikiPage> findPageByTitleOrSlug(String nameOrSlug) {
        // First try exact title match
        Optional<WikiPage> byTitle = wikiPageRepository.findByTitle(nameOrSlug);
        if (byTitle.isPresent()) {
            return byTitle;
        }

        // Then try slug match
        String slug = generateSlug(nameOrSlug);
        return wikiPageRepository.findBySlug(slug);
    }

    /**
     * Get all pages
     */
    @Transactional(readOnly = true)
    public List<WikiPage> findAll() {
        return wikiPageRepository.findAll();
    }

    /**
     * Search pages by title or content
     */
    @Transactional(readOnly = true)
    public List<WikiPage> search(String query) {
        return wikiPageRepository.searchByTitleOrContent(query);
    }

    // ==================== Hierarchy Operations ====================

    /**
     * Get all root pages (pages without parent)
     */
    @Transactional(readOnly = true)
    public List<WikiPage> getRootPages() {
        return wikiPageRepository.findAllRootPages();
    }

    /**
     * Get children of a page
     */
    @Transactional(readOnly = true)
    public List<WikiPage> getChildren(Long parentId) {
        return wikiPageRepository.findChildrenByParentId(parentId);
    }

    /**
     * Build a tree structure starting from root pages
     *
     * @return list of root pages with children populated
     */
    @Transactional(readOnly = true)
    public List<WikiPage> getPageTree() {
        return getRootPages();
    }

    /**
     * Move a page to a new parent
     *
     * @param pageId   the ID of the page to move
     * @param newParentId the ID of the new parent (null for root)
     * @return the updated page
     */
    @Transactional
    public WikiPage movePage(Long pageId, Long newParentId) {
        WikiPage page = wikiPageRepository.findById(pageId)
                .orElseThrow(() -> new IllegalArgumentException("Page not found: " + pageId));

        // Prevent circular reference
        if (newParentId != null) {
            if (newParentId.equals(pageId)) {
                throw new IllegalArgumentException("A page cannot be its own parent");
            }
            // Check if new parent is a descendant of this page
            if (isDescendant(newParentId, pageId)) {
                throw new IllegalArgumentException("Cannot move a page under its own descendant");
            }

            WikiPage newParent = wikiPageRepository.findById(newParentId)
                    .orElseThrow(() -> new IllegalArgumentException("Parent page not found: " + newParentId));
            page.setParent(newParent);
        } else {
            page.setParent(null);
        }

        return wikiPageRepository.save(page);
    }

    /**
     * Check if a page is a descendant of another page
     */
    private boolean isDescendant(Long pageId, Long potentialAncestorId) {
        WikiPage page = wikiPageRepository.findById(pageId).orElse(null);
        while (page != null && page.getParent() != null) {
            if (page.getParent().getId().equals(potentialAncestorId)) {
                return true;
            }
            page = page.getParent();
        }
        return false;
    }

    // ==================== Delete Operations ====================

    /**
     * Delete a page by ID
     * Children will be moved to the deleted page's parent
     *
     * @param pageId the ID of the page to delete
     */
    @Transactional
    public void deletePage(Long pageId) {
        WikiPage page = wikiPageRepository.findById(pageId)
                .orElseThrow(() -> new IllegalArgumentException("Page not found: " + pageId));

        // Move children to parent (or make them root if no parent)
        WikiPage newParent = page.getParent();
        for (WikiPage child : new ArrayList<>(page.getChildren())) {
            child.setParent(newParent);
            wikiPageRepository.save(child);
        }

        // Clear links to avoid constraint violations
        page.clearLinksTo();
        wikiPageRepository.save(page);

        // Delete the page
        wikiPageRepository.delete(page);
        log.info("Deleted wiki page: {} (id: {})", page.getTitle(), pageId);
    }

    // ==================== Slug Generation ====================

    /**
     * Generate a URL-friendly slug from a title
     *
     * @param title the title to convert
     * @return the generated slug
     */
    public String generateSlug(String title) {
        if (title == null || title.isBlank()) {
            return "";
        }

        // Normalize unicode characters
        String normalized = Normalizer.normalize(title, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");

        // Convert to lowercase and replace spaces/special chars with hyphens
        String slug = normalized.toLowerCase()
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("\\s+", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");

        return slug.isEmpty() ? "page" : slug;
    }

    /**
     * Generate a unique slug, appending a number if necessary
     *
     * @param title the title to convert
     * @return a unique slug
     */
    public String generateUniqueSlug(String title) {
        String baseSlug = generateSlug(title);
        String slug = baseSlug;
        int counter = 1;

        while (wikiPageRepository.existsBySlug(slug)) {
            slug = baseSlug + "-" + counter;
            counter++;
        }

        return slug;
    }

    // ==================== Validation ====================

    /**
     * Validate a wiki page before saving
     */
    private void validatePage(WikiPage page) {
        if (page.getTitle() == null || page.getTitle().isBlank()) {
            throw new IllegalArgumentException("Page title is required");
        }

        // Check for duplicate title (if new page or title changed)
        Optional<WikiPage> existingByTitle = wikiPageRepository.findByTitle(page.getTitle());
        if (existingByTitle.isPresent() && !existingByTitle.get().getId().equals(page.getId())) {
            throw new IllegalArgumentException("A page with this title already exists: " + page.getTitle());
        }
    }

    // ==================== Utility Methods ====================

    /**
     * Find orphan pages (pages with no backlinks and no parent)
     */
    @Transactional(readOnly = true)
    public List<WikiPage> findOrphanPages() {
        return wikiPageRepository.findOrphanPages();
    }

    /**
     * Get recently updated pages
     */
    @Transactional(readOnly = true)
    public List<WikiPage> getRecentlyUpdatedPages() {
        return wikiPageRepository.findRecentlyUpdated();
    }

    /**
     * Reparse all pages for backlinks
     * Useful after bulk import or when fixing link issues
     */
    @Transactional
    public void reparseAllBacklinks() {
        List<WikiPage> allPages = wikiPageRepository.findAll();
        log.info("Reparsing backlinks for {} pages", allPages.size());

        for (WikiPage page : allPages) {
            updateBacklinks(page);
            wikiPageRepository.save(page);
        }

        log.info("Finished reparsing all backlinks");
    }
}