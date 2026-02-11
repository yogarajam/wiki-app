package com.wiki.repository;

import com.wiki.model.Attachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for Attachment entity
 */
@Repository
public interface AttachmentRepository extends JpaRepository<Attachment, Long> {

    /**
     * Find attachment by object key
     */
    Optional<Attachment> findByObjectKey(String objectKey);

    /**
     * Find all attachments for a wiki page
     */
    List<Attachment> findByWikiPageId(Long wikiPageId);

    /**
     * Delete all attachments for a wiki page
     */
    void deleteByWikiPageId(Long wikiPageId);

    /**
     * Check if attachment exists by object key
     */
    boolean existsByObjectKey(String objectKey);
}