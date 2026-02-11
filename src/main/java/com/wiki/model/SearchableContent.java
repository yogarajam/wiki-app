package com.wiki.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Entity for storing extracted text content from attachments.
 * Used for full-text search across documents.
 */
@Entity
@Table(name = "searchable_content", indexes = {
        @Index(name = "idx_searchable_attachment", columnList = "attachment_id"),
        @Index(name = "idx_searchable_wiki_page", columnList = "wiki_page_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SearchableContent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attachment_id", unique = true)
    private Attachment attachment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wiki_page_id")
    private WikiPage wikiPage;

    /**
     * The extracted text content from the document.
     * Stored as TEXT type in PostgreSQL for large content.
     */
    @Column(name = "extracted_text", columnDefinition = "TEXT")
    private String extractedText;

    /**
     * Original filename for display purposes.
     */
    @Column(name = "filename")
    private String filename;

    /**
     * Content type of the original document.
     */
    @Column(name = "content_type")
    private String contentType;

    /**
     * Document title extracted from metadata (if available).
     */
    @Column(name = "document_title")
    private String documentTitle;

    /**
     * Document author extracted from metadata (if available).
     */
    @Column(name = "document_author")
    private String documentAuthor;

    /**
     * Number of characters in extracted text.
     */
    @Column(name = "text_length")
    private Integer textLength;

    /**
     * Status of the extraction process.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "extraction_status", nullable = false)
    private ExtractionStatus extractionStatus;

    /**
     * Error message if extraction failed.
     */
    @Column(name = "error_message")
    private String errorMessage;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Extraction status enumeration.
     */
    public enum ExtractionStatus {
        PENDING,
        PROCESSING,
        COMPLETED,
        FAILED,
        UNSUPPORTED
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SearchableContent that = (SearchableContent) o;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}