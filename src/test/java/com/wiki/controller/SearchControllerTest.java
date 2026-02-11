package com.wiki.controller;

import com.wiki.dto.SearchResultDTO;
import com.wiki.model.SearchableContent;
import com.wiki.model.WikiPage;
import com.wiki.service.SearchService;
import com.wiki.service.SearchService.SearchResults;
import com.wiki.service.SearchService.SearchStats;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Search Controller Tests")
class SearchControllerTest {

    @Mock
    private SearchService searchService;

    @InjectMocks
    private SearchController controller;

    @Nested
    @DisplayName("Unified Search Tests")
    class UnifiedSearchTests {

        @Test
        @DisplayName("Should return search results with pages and attachments")
        void shouldReturnSearchResults() {
            WikiPage page = WikiPage.builder()
                    .id(1L)
                    .title("Test Page")
                    .slug("test-page")
                    .content("Test content for search")
                    .build();

            SearchResults results = SearchResults.of(
                    List.of(page),
                    Collections.emptyList(),
                    "test"
            );

            when(searchService.search("test")).thenReturn(results);

            ResponseEntity<SearchResultDTO> response = controller.search("test");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().getQuery()).isEqualTo("test");
            assertThat(response.getBody().getTotalResults()).isEqualTo(1);
            assertThat(response.getBody().getPageResults()).hasSize(1);
        }

        @Test
        @DisplayName("Should return empty results when no matches")
        void shouldReturnEmptyResults() {
            when(searchService.search("nonexistent")).thenReturn(SearchResults.empty());

            ResponseEntity<SearchResultDTO> response = controller.search("nonexistent");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().getTotalResults()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("Attachment Search Tests")
    class AttachmentSearchTests {

        @Test
        @DisplayName("Should return attachment search results")
        void shouldReturnAttachmentResults() {
            when(searchService.searchAttachments("pdf"))
                    .thenReturn(Collections.emptyList());

            ResponseEntity<List<SearchResultDTO.AttachmentResult>> response =
                    controller.searchAttachments("pdf");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isEmpty();
        }

        @Test
        @DisplayName("Should search attachments in specific page")
        void shouldSearchAttachmentsInPage() {
            when(searchService.searchAttachmentsInPage(1L, "query"))
                    .thenReturn(Collections.emptyList());

            ResponseEntity<List<SearchResultDTO.AttachmentResult>> response =
                    controller.searchAttachmentsInPage(1L, "query");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            verify(searchService).searchAttachmentsInPage(1L, "query");
        }
    }

    @Nested
    @DisplayName("Stats Tests")
    class StatsTests {

        @Test
        @DisplayName("Should return search statistics")
        void shouldReturnStats() {
            SearchStats stats = new SearchStats(10, 8, 1, 1);
            when(searchService.getSearchStats()).thenReturn(stats);

            ResponseEntity<SearchStats> response = controller.getStats();

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().totalDocuments()).isEqualTo(10);
        }
    }

    @Nested
    @DisplayName("Reindex Tests")
    class ReindexTests {

        @Test
        @DisplayName("Should trigger full reindex")
        void shouldTriggerFullReindex() {
            ResponseEntity<String> response = controller.reindexAll();

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).contains("Reindex started");
            verify(searchService).reindexAll();
        }

        @Test
        @DisplayName("Should trigger page reindex")
        void shouldTriggerPageReindex() {
            ResponseEntity<String> response = controller.reindexPage(1L);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            verify(searchService).reindexPage(1L);
        }
    }
}