package com.wiki.controller;

import com.wiki.dto.AttachmentDTO;
import com.wiki.dto.UploadResponseDTO;
import com.wiki.model.Attachment;
import com.wiki.model.WikiPage;
import com.wiki.repository.AttachmentRepository;
import com.wiki.service.MinioStorageService;
import com.wiki.service.SearchService;
import com.wiki.service.WikiPageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.stream.Collectors;

/**
 * REST Controller for file attachment operations
 */
@RestController
@RequestMapping("/api/attachments")
@RequiredArgsConstructor
@Slf4j
public class AttachmentController {

    private final MinioStorageService storageService;
    private final AttachmentRepository attachmentRepository;
    private final WikiPageService wikiPageService;
    private final SearchService searchService;

    /**
     * Upload a file to MinIO
     */
    @PostMapping("/upload")
    public ResponseEntity<UploadResponseDTO> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "pathPrefix", required = false) String pathPrefix,
            @RequestParam(value = "pageId", required = false) Long pageId) {

        try {
            // Upload to MinIO
            String objectKey = storageService.uploadFile(file, pathPrefix != null ? pathPrefix : "");
            String url = storageService.getFileUrl(objectKey);

            // Create attachment record if pageId provided
            Attachment attachment = null;
            if (pageId != null) {
                WikiPage page = wikiPageService.findById(pageId).orElse(null);
                if (page != null) {
                    attachment = Attachment.builder()
                            .originalFilename(file.getOriginalFilename())
                            .objectKey(objectKey)
                            .contentType(file.getContentType())
                            .fileSize(file.getSize())
                            .wikiPage(page)
                            .build();
                    attachment = attachmentRepository.save(attachment);

                    // Trigger async text extraction for search indexing
                    searchService.indexAttachment(attachment);
                }
            }

            UploadResponseDTO response = new UploadResponseDTO();
            response.setSuccess(true);
            response.setUrl(url);
            response.setObjectKey(objectKey);
            response.setOriginalFilename(file.getOriginalFilename());
            response.setContentType(file.getContentType());
            response.setFileSize(file.getSize());
            if (attachment != null) {
                response.setAttachmentId(attachment.getId());
            }

            log.info("File uploaded successfully: {}", objectKey);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to upload file: {}", e.getMessage(), e);
            UploadResponseDTO errorResponse = new UploadResponseDTO();
            errorResponse.setSuccess(false);
            errorResponse.setError(e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * Get all attachments for a page
     */
    @GetMapping("/page/{pageId}")
    public ResponseEntity<List<AttachmentDTO>> getAttachmentsForPage(@PathVariable Long pageId) {
        List<Attachment> attachments = attachmentRepository.findByWikiPageId(pageId);
        List<AttachmentDTO> dtos = attachments.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    /**
     * Delete an attachment
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAttachment(@PathVariable Long id) {
        return attachmentRepository.findById(id)
                .map(attachment -> {
                    try {
                        // Remove from search index
                        searchService.removeFromIndex(attachment.getId());
                        // Delete from MinIO
                        storageService.deleteFile(attachment.getObjectKey());
                        // Delete from database
                        attachmentRepository.delete(attachment);
                        return ResponseEntity.noContent().<Void>build();
                    } catch (Exception e) {
                        log.error("Failed to delete attachment: {}", e.getMessage(), e);
                        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).<Void>build();
                    }
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get presigned URL for downloading a file
     */
    @GetMapping("/{id}/download-url")
    public ResponseEntity<String> getDownloadUrl(@PathVariable Long id) {
        return attachmentRepository.findById(id)
                .map(attachment -> {
                    String url = storageService.getPresignedUrl(attachment.getObjectKey(), 3600);
                    return ResponseEntity.ok(url);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    private AttachmentDTO convertToDTO(Attachment attachment) {
        AttachmentDTO dto = new AttachmentDTO();
        dto.setId(attachment.getId());
        dto.setOriginalFilename(attachment.getOriginalFilename());
        dto.setObjectKey(attachment.getObjectKey());
        dto.setContentType(attachment.getContentType());
        dto.setFileSize(attachment.getFileSize());
        dto.setUrl(storageService.getFileUrl(attachment.getObjectKey()));
        dto.setUploadedAt(attachment.getUploadedAt());
        return dto;
    }
}