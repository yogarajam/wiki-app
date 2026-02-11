package com.wiki.repository;

import com.wiki.model.WikiPage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Repository for WikiPage entity
 * Provides methods for hierarchical queries and backlink management
 */
@Repository
public interface WikiPageRepository extends JpaRepository<WikiPage, Long> {

    // ==================== Basic Queries ====================

    /**
     * Find a page by its slug
     */
    Optional<WikiPage> findBySlug(String slug);

    /**
     * Find a page by its title
     */
    Optional<WikiPage> findByTitle(String title);

    /**
     * Check if a page exists with the given slug
     */
    boolean existsBySlug(String slug);

    /**
     * Check if a page exists with the given title
     */
    boolean existsByTitle(String title);

    /**
     * Find all pages by title (case-insensitive search)
     */
    List<WikiPage> findByTitleContainingIgnoreCase(String title);

    // ==================== Hierarchy Queries ====================

    /**
     * Find all root pages (pages without a parent)
     */
    @Query("SELECT w FROM WikiPage w WHERE w.parent IS NULL ORDER BY w.title")
    List<WikiPage> findAllRootPages();

    /**
     * Find all children of a specific page
     */
    @Query("SELECT w FROM WikiPage w WHERE w.parent.id = :parentId ORDER BY w.title")
    List<WikiPage> findChildrenByParentId(@Param("parentId") Long parentId);

    /**
     * Find all descendants of a page (recursive)
     * Uses recursive CTE for PostgreSQL
     */
    @Query(value = """
            WITH RECURSIVE descendants AS (
                SELECT id, title, slug, parent_id, 0 as depth
                FROM wiki_pages
                WHERE parent_id = :parentId
                UNION ALL
                SELECT wp.id, wp.title, wp.slug, wp.parent_id, d.depth + 1
                FROM wiki_pages wp
                INNER JOIN descendants d ON wp.parent_id = d.id
            )
            SELECT * FROM descendants ORDER BY depth, title
            """, nativeQuery = true)
    List<Object[]> findAllDescendantsNative(@Param("parentId") Long parentId);

    /**
     * Find all ancestors of a page (path to root)
     */
    @Query(value = """
            WITH RECURSIVE ancestors AS (
                SELECT id, title, slug, parent_id, 0 as depth
                FROM wiki_pages
                WHERE id = :pageId
                UNION ALL
                SELECT wp.id, wp.title, wp.slug, wp.parent_id, a.depth + 1
                FROM wiki_pages wp
                INNER JOIN ancestors a ON wp.id = a.parent_id
            )
            SELECT * FROM ancestors WHERE id != :pageId ORDER BY depth DESC
            """, nativeQuery = true)
    List<Object[]> findAllAncestorsNative(@Param("pageId") Long pageId);

    /**
     * Count children of a page
     */
    @Query("SELECT COUNT(w) FROM WikiPage w WHERE w.parent.id = :parentId")
    long countChildrenByParentId(@Param("parentId") Long parentId);

    // ==================== Backlink Queries ====================

    /**
     * Find all pages that link to a specific page (backlinks)
     */
    @Query("SELECT DISTINCT w FROM WikiPage w JOIN w.linksTo lt WHERE lt.id = :pageId")
    List<WikiPage> findPagesLinkingTo(@Param("pageId") Long pageId);

    /**
     * Find all pages that a specific page links to
     */
    @Query("SELECT w.linksTo FROM WikiPage w WHERE w.id = :pageId")
    Set<WikiPage> findPagesLinkedFrom(@Param("pageId") Long pageId);

    /**
     * Count backlinks for a page
     */
    @Query("SELECT COUNT(DISTINCT w) FROM WikiPage w JOIN w.linksTo lt WHERE lt.id = :pageId")
    long countBacklinks(@Param("pageId") Long pageId);

    /**
     * Find pages with no backlinks (orphan pages)
     */
    @Query("""
            SELECT w FROM WikiPage w
            WHERE w.id NOT IN (
                SELECT DISTINCT lt.id FROM WikiPage wp JOIN wp.linksTo lt
            )
            AND w.parent IS NULL
            ORDER BY w.title
            """)
    List<WikiPage> findOrphanPages();

    // ==================== Search Queries ====================

    /**
     * Full-text search in title, content, and attachment filenames
     */
    @Query("""
            SELECT DISTINCT w FROM WikiPage w
            LEFT JOIN w.attachments a
            WHERE LOWER(w.title) LIKE LOWER(CONCAT('%', :query, '%'))
            OR LOWER(w.content) LIKE LOWER(CONCAT('%', :query, '%'))
            OR LOWER(a.originalFilename) LIKE LOWER(CONCAT('%', :query, '%'))
            ORDER BY w.title
            """)
    List<WikiPage> searchByTitleOrContent(@Param("query") String query);

    /**
     * Find all published pages
     */
    List<WikiPage> findByPublishedTrueOrderByTitleAsc();

    /**
     * Find recently updated pages
     */
    @Query("SELECT w FROM WikiPage w ORDER BY w.updatedAt DESC")
    List<WikiPage> findRecentlyUpdated();

    // ==================== Slug Queries ====================

    /**
     * Find pages by slug pattern (for generating unique slugs)
     */
    @Query("SELECT w.slug FROM WikiPage w WHERE w.slug LIKE :slugPattern")
    List<String> findSlugsByPattern(@Param("slugPattern") String slugPattern);
}