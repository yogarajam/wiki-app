package com.wiki.controller;

import com.wiki.dto.WikiPageDTO;
import com.wiki.dto.WikiPageTreeDTO;
import com.wiki.model.WikiPage;
import com.wiki.security.SecurityValidator;
import com.wiki.service.WikiPageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("WikiPage Controller Tests")
class WikiPageControllerTest {

    @Mock
    private WikiPageService wikiPageService;

    @Mock
    private SecurityValidator securityValidator;

    @InjectMocks
    private WikiPageController controller;

    private WikiPage samplePage;

    @BeforeEach
    void setUp() {
        samplePage = WikiPage.builder()
                .id(1L)
                .title("Test Page")
                .slug("test-page")
                .content("Test content")
                .version(1)
                .published(false)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    @Nested
    @DisplayName("Get Page Tree Tests")
    class GetPageTreeTests {

        @Test
        @DisplayName("Should return page tree with root pages")
        void shouldReturnPageTree() {
            when(wikiPageService.getRootPages()).thenReturn(List.of(samplePage));

            ResponseEntity<List<WikiPageTreeDTO>> response = controller.getPageTree();

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).hasSize(1);
            assertThat(response.getBody().get(0).getTitle()).isEqualTo("Test Page");
        }

        @Test
        @DisplayName("Should return empty tree when no pages")
        void shouldReturnEmptyTree() {
            when(wikiPageService.getRootPages()).thenReturn(Collections.emptyList());

            ResponseEntity<List<WikiPageTreeDTO>> response = controller.getPageTree();

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isEmpty();
        }

        @Test
        @DisplayName("Should include children in tree")
        void shouldIncludeChildrenInTree() {
            WikiPage child = WikiPage.builder()
                    .id(2L)
                    .title("Child Page")
                    .slug("child-page")
                    .build();
            samplePage.addChild(child);

            when(wikiPageService.getRootPages()).thenReturn(List.of(samplePage));

            ResponseEntity<List<WikiPageTreeDTO>> response = controller.getPageTree();

            assertThat(response.getBody()).hasSize(1);
            WikiPageTreeDTO rootDto = response.getBody().get(0);
            assertThat(rootDto.getChildren()).hasSize(1);
            assertThat(rootDto.getChildren().get(0).getTitle()).isEqualTo("Child Page");
        }
    }

    @Nested
    @DisplayName("Get Page By ID Tests")
    class GetPageByIdTests {

        @Test
        @DisplayName("Should return page when found")
        void shouldReturnPageWhenFound() {
            when(wikiPageService.findById(1L)).thenReturn(Optional.of(samplePage));

            ResponseEntity<WikiPageDTO> response = controller.getPageById(1L);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().getTitle()).isEqualTo("Test Page");
            assertThat(response.getBody().getSlug()).isEqualTo("test-page");
        }

        @Test
        @DisplayName("Should return 404 when page not found")
        void shouldReturn404WhenNotFound() {
            when(wikiPageService.findById(999L)).thenReturn(Optional.empty());

            ResponseEntity<WikiPageDTO> response = controller.getPageById(999L);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("Should set parentId in DTO when page has parent")
        void shouldSetParentIdInDto() {
            WikiPage parent = WikiPage.builder().id(10L).title("Parent").build();
            samplePage.setParent(parent);
            when(wikiPageService.findById(1L)).thenReturn(Optional.of(samplePage));

            ResponseEntity<WikiPageDTO> response = controller.getPageById(1L);

            assertThat(response.getBody().getParentId()).isEqualTo(10L);
        }
    }

    @Nested
    @DisplayName("Get Page By Slug Tests")
    class GetPageBySlugTests {

        @Test
        @DisplayName("Should return page by slug")
        void shouldReturnPageBySlug() {
            when(wikiPageService.findBySlug("test-page")).thenReturn(Optional.of(samplePage));

            ResponseEntity<WikiPageDTO> response = controller.getPageBySlug("test-page");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().getSlug()).isEqualTo("test-page");
        }

        @Test
        @DisplayName("Should return 404 for unknown slug")
        void shouldReturn404ForUnknownSlug() {
            when(wikiPageService.findBySlug("unknown")).thenReturn(Optional.empty());

            ResponseEntity<WikiPageDTO> response = controller.getPageBySlug("unknown");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("Search Pages Tests")
    class SearchPagesTests {

        @Test
        @DisplayName("Should return search results")
        void shouldReturnSearchResults() {
            when(wikiPageService.search("test")).thenReturn(List.of(samplePage));

            ResponseEntity<List<WikiPageDTO>> response = controller.searchPages("test");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).hasSize(1);
        }

        @Test
        @DisplayName("Should return empty results for no matches")
        void shouldReturnEmptyForNoMatches() {
            when(wikiPageService.search("nonexistent")).thenReturn(Collections.emptyList());

            ResponseEntity<List<WikiPageDTO>> response = controller.searchPages("nonexistent");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Create Page Tests")
    class CreatePageTests {

        @Test
        @DisplayName("Should create page and return 201")
        void shouldCreatePageAndReturn201() {
            WikiPageDTO dto = new WikiPageDTO();
            dto.setTitle("New Page");
            dto.setContent("New content");

            when(wikiPageService.savePage(any(WikiPage.class), eq(null)))
                    .thenReturn(samplePage);

            ResponseEntity<WikiPageDTO> response = controller.createPage(dto);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(response.getBody()).isNotNull();
        }

        @Test
        @DisplayName("Should create page with parent")
        void shouldCreatePageWithParent() {
            WikiPageDTO dto = new WikiPageDTO();
            dto.setTitle("Child Page");
            dto.setContent("Child content");
            dto.setParentId(10L);

            when(wikiPageService.savePage(any(WikiPage.class), eq(10L)))
                    .thenReturn(samplePage);

            ResponseEntity<WikiPageDTO> response = controller.createPage(dto);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            verify(wikiPageService).savePage(any(WikiPage.class), eq(10L));
        }
    }

    @Nested
    @DisplayName("Update Page Tests")
    class UpdatePageTests {

        @Test
        @DisplayName("Should update existing page")
        void shouldUpdateExistingPage() {
            WikiPageDTO dto = new WikiPageDTO();
            dto.setTitle("Updated Title");
            dto.setContent("Updated content");

            when(wikiPageService.findById(1L)).thenReturn(Optional.of(samplePage));
            when(wikiPageService.savePage(any(WikiPage.class))).thenReturn(samplePage);

            ResponseEntity<WikiPageDTO> response = controller.updatePage(1L, dto);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("Should return 404 when updating non-existent page")
        void shouldReturn404WhenUpdatingNonExistent() {
            WikiPageDTO dto = new WikiPageDTO();
            dto.setTitle("Updated");

            when(wikiPageService.findById(999L)).thenReturn(Optional.empty());

            ResponseEntity<WikiPageDTO> response = controller.updatePage(999L, dto);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("Delete Page Tests")
    class DeletePageTests {

        @Test
        @DisplayName("Should delete existing page and return 204")
        void shouldDeleteExistingPage() {
            when(wikiPageService.findById(1L)).thenReturn(Optional.of(samplePage));

            ResponseEntity<Void> response = controller.deletePage(1L);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
            verify(wikiPageService).deletePage(1L);
        }

        @Test
        @DisplayName("Should return 404 when deleting non-existent page")
        void shouldReturn404WhenDeletingNonExistent() {
            when(wikiPageService.findById(999L)).thenReturn(Optional.empty());

            ResponseEntity<Void> response = controller.deletePage(999L);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            verify(wikiPageService, never()).deletePage(anyLong());
        }
    }

    @Nested
    @DisplayName("Get Backlinks Tests")
    class GetBacklinksTests {

        @Test
        @DisplayName("Should return backlinks for a page")
        void shouldReturnBacklinks() {
            WikiPage linkedPage = WikiPage.builder()
                    .id(2L)
                    .title("Linked Page")
                    .slug("linked-page")
                    .version(1)
                    .published(false)
                    .build();

            when(wikiPageService.getBacklinks(1L)).thenReturn(List.of(linkedPage));

            ResponseEntity<List<WikiPageDTO>> response = controller.getBacklinks(1L);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).hasSize(1);
            assertThat(response.getBody().get(0).getTitle()).isEqualTo("Linked Page");
        }
    }

    @Nested
    @DisplayName("Move Page Tests")
    class MovePageTests {

        @Test
        @DisplayName("Should move page to new parent")
        void shouldMovePageToNewParent() {
            WikiPageController.MovePageRequest request = new WikiPageController.MovePageRequest();
            request.setParentId(5L);

            when(wikiPageService.movePage(1L, 5L)).thenReturn(samplePage);

            ResponseEntity<WikiPageDTO> response = controller.movePage(1L, request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            verify(wikiPageService).movePage(1L, 5L);
        }
    }
}