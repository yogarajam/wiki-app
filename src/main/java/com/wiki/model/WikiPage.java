package com.wiki.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * WikiPage Entity
 * Supports hierarchical structure (parent-child) for tree view
 * and bidirectional backlinks for discovering which pages link to this page
 */
@Entity
@Table(name = "wiki_pages", indexes = {
        @Index(name = "idx_wiki_page_slug", columnList = "slug", unique = true),
        @Index(name = "idx_wiki_page_parent", columnList = "parent_id"),
        @Index(name = "idx_wiki_page_title", columnList = "title")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WikiPage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, unique = true)
    private String slug;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Builder.Default
    @Column(nullable = false)
    private Boolean published = false;

    @Builder.Default
    @Column(nullable = false)
    private Boolean folder = false;

    @Builder.Default
    @Column(nullable = false)
    private Integer version = 1;

    // ==================== Hierarchy (Parent-Child) ====================

    /**
     * Parent page for hierarchical structure
     * A page can have one parent (or null for root pages)
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private WikiPage parent;

    /**
     * Child pages for hierarchical structure
     * A page can have multiple children
     */
    @OneToMany(mappedBy = "parent", cascade = CascadeType.ALL, orphanRemoval = false)
    @Builder.Default
    @OrderBy("title ASC")
    private List<WikiPage> children = new ArrayList<>();

    // ==================== Backlinks ====================

    /**
     * Pages that THIS page links TO (outgoing links)
     * When content contains [[OtherPage]], OtherPage is added here
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "wiki_page_links",
            joinColumns = @JoinColumn(name = "source_page_id"),
            inverseJoinColumns = @JoinColumn(name = "target_page_id")
    )
    @Builder.Default
    private Set<WikiPage> linksTo = new HashSet<>();

    /**
     * Pages that link TO this page (incoming links / backlinks)
     * This is the inverse side - automatically populated when other pages link here
     */
    @ManyToMany(mappedBy = "linksTo", fetch = FetchType.LAZY)
    @Builder.Default
    private Set<WikiPage> linkedFrom = new HashSet<>();

    // ==================== Attachments ====================

    /**
     * File attachments stored in MinIO
     */
    @OneToMany(mappedBy = "wikiPage", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Attachment> attachments = new ArrayList<>();

    // ==================== Metadata ====================

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "updated_by")
    private String updatedBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // ==================== Helper Methods ====================

    /**
     * Add a child page to this page
     */
    public void addChild(WikiPage child) {
        children.add(child);
        child.setParent(this);
    }

    /**
     * Remove a child page from this page
     */
    public void removeChild(WikiPage child) {
        children.remove(child);
        child.setParent(null);
    }

    /**
     * Add an outgoing link to another page
     */
    public void addLinkTo(WikiPage targetPage) {
        linksTo.add(targetPage);
    }

    /**
     * Remove an outgoing link to another page
     */
    public void removeLinkTo(WikiPage targetPage) {
        linksTo.remove(targetPage);
    }

    /**
     * Clear all outgoing links
     */
    public void clearLinksTo() {
        linksTo.clear();
    }

    /**
     * Add an attachment to this page
     */
    public void addAttachment(Attachment attachment) {
        attachments.add(attachment);
        attachment.setWikiPage(this);
    }

    /**
     * Remove an attachment from this page
     */
    public void removeAttachment(Attachment attachment) {
        attachments.remove(attachment);
        attachment.setWikiPage(null);
    }

    /**
     * Check if this is a root page (no parent)
     */
    public boolean isRoot() {
        return parent == null;
    }

    /**
     * Check if this page has children
     */
    public boolean hasChildren() {
        return children != null && !children.isEmpty();
    }

    /**
     * Get the depth of this page in the hierarchy (0 for root)
     */
    public int getDepth() {
        int depth = 0;
        WikiPage current = this.parent;
        while (current != null) {
            depth++;
            current = current.getParent();
        }
        return depth;
    }

    /**
     * Get the full path from root to this page
     */
    public List<WikiPage> getPath() {
        List<WikiPage> path = new ArrayList<>();
        WikiPage current = this;
        while (current != null) {
            path.add(0, current);
            current = current.getParent();
        }
        return path;
    }

    /**
     * Get the number of backlinks (pages linking to this page)
     */
    public int getBacklinkCount() {
        return linkedFrom != null ? linkedFrom.size() : 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        WikiPage wikiPage = (WikiPage) o;
        return id != null && id.equals(wikiPage.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "WikiPage{" +
                "id=" + id +
                ", title='" + title + '\'' +
                ", slug='" + slug + '\'' +
                '}';
    }
}