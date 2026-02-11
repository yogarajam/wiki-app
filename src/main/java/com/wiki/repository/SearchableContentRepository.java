package com.wiki.repository;

import com.wiki.model.SearchableContent;
import com.wiki.model.SearchableContent.ExtractionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for SearchableContent entity.
 * Provides methods for full-text search across extracted document content.
 */
@Repository
public interface SearchableContentRepository extends JpaRepository<SearchableContent, Long> {

    /**
     * Find searchable content by attachment ID.
     */
    Optional<SearchableContent> findByAttachmentId(Long attachmentId);

    /**
     * Find all searchable content for a wiki page.
     */
    List<SearchableContent> findByWikiPageId(Long wikiPageId);

    /**
     * Find content by extraction status.
     */
    List<SearchableContent> findByExtractionStatus(ExtractionStatus status);

    /**
     * Find pending extractions (for batch processing).
     */
    @Query("SELECT sc FROM SearchableContent sc WHERE sc.extractionStatus = 'PENDING' ORDER BY sc.createdAt ASC")
    List<SearchableContent> findPendingExtractions();

    /**
     * Full-text search in extracted content (case-insensitive).
     */
    @Query("""
            SELECT sc FROM SearchableContent sc
            WHERE sc.extractionStatus = 'COMPLETED'
            AND (
                LOWER(sc.extractedText) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(sc.filename) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(sc.documentTitle) LIKE LOWER(CONCAT('%', :query, '%'))
            )
            ORDER BY sc.updatedAt DESC
            """)
    List<SearchableContent> searchByContent(@Param("query") String query);

    /**
     * Full-text search with pagination.
     */
    @Query(value = """
            SELECT sc.* FROM searchable_content sc
            WHERE sc.extraction_status = 'COMPLETED'
            AND (
                LOWER(sc.extracted_text) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(sc.filename) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(sc.document_title) LIKE LOWER(CONCAT('%', :query, '%'))
            )
            ORDER BY sc.updated_at DESC
            LIMIT :limit OFFSET :offset
            """, nativeQuery = true)
    List<SearchableContent> searchByContentPaginated(
            @Param("query") String query,
            @Param("limit") int limit,
            @Param("offset") int offset);

    /**
     * Search within a specific wiki page's attachments.
     */
    @Query("""
            SELECT sc FROM SearchableContent sc
            WHERE sc.wikiPage.id = :pageId
            AND sc.extractionStatus = 'COMPLETED'
            AND LOWER(sc.extractedText) LIKE LOWER(CONCAT('%', :query, '%'))
            """)
    List<SearchableContent> searchByContentInPage(
            @Param("pageId") Long pageId,
            @Param("query") String query);

    /**
     * Count total searchable documents.
     */
    @Query("SELECT COUNT(sc) FROM SearchableContent sc WHERE sc.extractionStatus = 'COMPLETED'")
    long countSearchableDocuments();

    /**
     * Delete searchable content by attachment ID.
     */
    void deleteByAttachmentId(Long attachmentId);

    /**
     * Delete all searchable content for a wiki page.
     */
    void deleteByWikiPageId(Long wikiPageId);

    /**
     * Check if content exists for an attachment.
     */
    boolean existsByAttachmentId(Long attachmentId);
}