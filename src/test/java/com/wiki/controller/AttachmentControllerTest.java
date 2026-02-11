package com.wiki.controller;

import com.wiki.dto.AttachmentDTO;
import com.wiki.dto.UploadResponseDTO;
import com.wiki.model.Attachment;
import com.wiki.model.WikiPage;
import com.wiki.repository.AttachmentRepository;
import com.wiki.service.MinioStorageService;
import com.wiki.service.SearchService;
import com.wiki.service.WikiPageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Attachment Controller Tests")
class AttachmentControllerTest {

    @Mock
    private MinioStorageService storageService;

    @Mock
    private AttachmentRepository attachmentRepository;

    @Mock
    private WikiPageService wikiPageService;

    @Mock
    private SearchService searchService;

    @InjectMocks
    private AttachmentController controller;

    @Nested
    @DisplayName("Upload File Tests")
    class UploadFileTests {

        @Test
        @DisplayName("Should upload file successfully without page association")
        void shouldUploadFileWithoutPage() {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "test.pdf", "application/pdf", "content".getBytes());

            when(storageService.uploadFile(any(), eq(""))).thenReturn("key-test.pdf");
            when(storageService.getFileUrl("key-test.pdf")).thenReturn("http://minio/key-test.pdf");

            ResponseEntity<UploadResponseDTO> response = controller.uploadFile(file, null, null);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().isSuccess()).isTrue();
            assertThat(response.getBody().getObjectKey()).isEqualTo("key-test.pdf");
        }

        @Test
        @DisplayName("Should upload file and associate with page")
        void shouldUploadFileWithPageAssociation() {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "doc.pdf", "application/pdf", "content".getBytes());

            WikiPage page = WikiPage.builder().id(1L).title("Page").build();
            Attachment attachment = Attachment.builder()
                    .id(1L)
                    .originalFilename("doc.pdf")
                    .objectKey("key-doc.pdf")
                    .build();

            when(storageService.uploadFile(any(), eq(""))).thenReturn("key-doc.pdf");
            when(storageService.getFileUrl("key-doc.pdf")).thenReturn("http://minio/key-doc.pdf");
            when(wikiPageService.findById(1L)).thenReturn(Optional.of(page));
            when(attachmentRepository.save(any(Attachment.class))).thenReturn(attachment);

            ResponseEntity<UploadResponseDTO> response = controller.uploadFile(file, null, 1L);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().isSuccess()).isTrue();
            assertThat(response.getBody().getAttachmentId()).isEqualTo(1L);
            verify(searchService).indexAttachment(any(Attachment.class));
        }

        @Test
        @DisplayName("Should handle upload failure")
        void shouldHandleUploadFailure() {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "test.pdf", "application/pdf", "content".getBytes());

            when(storageService.uploadFile(any(), any())).thenThrow(new RuntimeException("Upload failed"));

            ResponseEntity<UploadResponseDTO> response = controller.uploadFile(file, null, null);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
            assertThat(response.getBody().isSuccess()).isFalse();
            assertThat(response.getBody().getError()).contains("Upload failed");
        }
    }

    @Nested
    @DisplayName("Get Attachments Tests")
    class GetAttachmentsTests {

        @Test
        @DisplayName("Should return attachments for a page")
        void shouldReturnAttachmentsForPage() {
            Attachment attachment = Attachment.builder()
                    .id(1L)
                    .originalFilename("test.pdf")
                    .objectKey("key")
                    .contentType("application/pdf")
                    .fileSize(1024L)
                    .uploadedAt(LocalDateTime.now())
                    .build();

            when(attachmentRepository.findByWikiPageId(1L)).thenReturn(List.of(attachment));
            when(storageService.getFileUrl("key")).thenReturn("http://minio/key");

            ResponseEntity<List<AttachmentDTO>> response = controller.getAttachmentsForPage(1L);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).hasSize(1);
            assertThat(response.getBody().get(0).getOriginalFilename()).isEqualTo("test.pdf");
        }
    }

    @Nested
    @DisplayName("Delete Attachment Tests")
    class DeleteAttachmentTests {

        @Test
        @DisplayName("Should delete attachment successfully")
        void shouldDeleteAttachment() {
            Attachment attachment = Attachment.builder()
                    .id(1L)
                    .originalFilename("test.pdf")
                    .objectKey("key")
                    .build();

            when(attachmentRepository.findById(1L)).thenReturn(Optional.of(attachment));

            ResponseEntity<Void> response = controller.deleteAttachment(1L);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
            verify(searchService).removeFromIndex(1L);
            verify(storageService).deleteFile("key");
            verify(attachmentRepository).delete(attachment);
        }

        @Test
        @DisplayName("Should return 404 when attachment not found")
        void shouldReturn404WhenNotFound() {
            when(attachmentRepository.findById(999L)).thenReturn(Optional.empty());

            ResponseEntity<Void> response = controller.deleteAttachment(999L);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("Download URL Tests")
    class DownloadUrlTests {

        @Test
        @DisplayName("Should return download URL")
        void shouldReturnDownloadUrl() {
            Attachment attachment = Attachment.builder()
                    .id(1L)
                    .objectKey("key")
                    .build();

            when(attachmentRepository.findById(1L)).thenReturn(Optional.of(attachment));
            when(storageService.getPresignedUrl("key", 3600)).thenReturn("http://minio/presigned/key");

            ResponseEntity<String> response = controller.getDownloadUrl(1L);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).contains("presigned");
        }

        @Test
        @DisplayName("Should return 404 for non-existent attachment")
        void shouldReturn404ForNonExistent() {
            when(attachmentRepository.findById(999L)).thenReturn(Optional.empty());

            ResponseEntity<String> response = controller.getDownloadUrl(999L);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }
}