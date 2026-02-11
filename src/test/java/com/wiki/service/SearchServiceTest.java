package com.wiki.service;

import com.wiki.model.Attachment;
import com.wiki.model.SearchableContent;
import com.wiki.model.SearchableContent.ExtractionStatus;
import com.wiki.model.WikiPage;
import com.wiki.repository.AttachmentRepository;
import com.wiki.repository.SearchableContentRepository;
import com.wiki.repository.WikiPageRepository;
import com.wiki.service.SearchService.SearchResults;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.s3.S3Client;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Search Service Tests")
class SearchServiceTest {

    @Mock
    private TextExtractionService textExtractionService;

    @Mock
    private SearchableContentRepository searchableContentRepository;

    @Mock
    private WikiPageRepository wikiPageRepository;

    @Mock
    private AttachmentRepository attachmentRepository;

    @Mock
    private S3Client s3Client;

    @InjectMocks
    private SearchService searchService;

    @Nested
    @DisplayName("Unified Search Tests")
    class UnifiedSearchTests {

        @Test
        @DisplayName("Should return empty results for null query")
        void shouldReturnEmptyForNullQuery() {
            SearchResults results = searchService.search(null);

            assertThat(results.isEmpty()).isTrue();
            assertThat(results.getTotalResults()).isEqualTo(0);
        }

        @Test
        @DisplayName("Should return empty results for blank query")
        void shouldReturnEmptyForBlankQuery() {
            SearchResults results = searchService.search("   ");

            assertThat(results.isEmpty()).isTrue();
        }

        @Test
        @DisplayName("Should return combined page and attachment results")
        void shouldReturnCombinedResults() {
            WikiPage page = WikiPage.builder().id(1L).title("Test Page").build();
            SearchableContent sc = SearchableContent.builder()
                    .id(1L)
                    .extractedText("test content")
                    .build();

            when(wikiPageRepository.searchByTitleOrContent("test")).thenReturn(List.of(page));
            when(searchableContentRepository.searchByContent("test")).thenReturn(List.of(sc));

            SearchResults results = searchService.search("test");

            assertThat(results.getTotalResults()).isEqualTo(2);
            assertThat(results.getPageResults()).hasSize(1);
            assertThat(results.getAttachmentResults()).hasSize(1);
            assertThat(results.getQuery()).isEqualTo("test");
        }

        @Test
        @DisplayName("Should trim query before searching")
        void shouldTrimQuery() {
            when(wikiPageRepository.searchByTitleOrContent("test")).thenReturn(Collections.emptyList());
            when(searchableContentRepository.searchByContent("test")).thenReturn(Collections.emptyList());

            searchService.search("  test  ");

            verify(wikiPageRepository).searchByTitleOrContent("test");
            verify(searchableContentRepository).searchByContent("test");
        }
    }

    @Nested
    @DisplayName("Attachment Search Tests")
    class AttachmentSearchTests {

        @Test
        @DisplayName("Should return empty list for null query")
        void shouldReturnEmptyForNullQuery() {
            var results = searchService.searchAttachments(null);
            assertThat(results).isEmpty();
        }

        @Test
        @DisplayName("Should return empty list for blank query")
        void shouldReturnEmptyForBlankQuery() {
            var results = searchService.searchAttachments("  ");
            assertThat(results).isEmpty();
        }

        @Test
        @DisplayName("Should search attachments within a specific page with null query")
        void shouldReturnEmptyForPageSearchWithNullQuery() {
            var results = searchService.searchAttachmentsInPage(1L, null);
            assertThat(results).isEmpty();
        }
    }

    @Nested
    @DisplayName("SearchResults Class Tests")
    class SearchResultsClassTests {

        @Test
        @DisplayName("Empty SearchResults should have zero total")
        void emptyResultsShouldHaveZeroTotal() {
            SearchResults results = SearchResults.empty();

            assertThat(results.isEmpty()).isTrue();
            assertThat(results.getTotalResults()).isEqualTo(0);
            assertThat(results.getPageResults()).isEmpty();
            assertThat(results.getAttachmentResults()).isEmpty();
            assertThat(results.getQuery()).isEmpty();
        }

        @Test
        @DisplayName("SearchResults.of should handle null lists")
        void ofShouldHandleNullLists() {
            SearchResults results = SearchResults.of(null, null, "query");

            assertThat(results.getPageResults()).isEmpty();
            assertThat(results.getAttachmentResults()).isEmpty();
            assertThat(results.getTotalResults()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("Index Removal Tests")
    class IndexRemovalTests {

        @Test
        @DisplayName("Should remove attachment from index")
        void shouldRemoveFromIndex() {
            searchService.removeFromIndex(1L);

            verify(searchableContentRepository).deleteByAttachmentId(1L);
        }
    }

    @Nested
    @DisplayName("Reindex Tests")
    class ReindexTests {

        @Test
        @DisplayName("Should reindex all attachments for a page")
        void shouldReindexPage() {
            Attachment attachment = Attachment.builder()
                    .id(1L)
                    .originalFilename("test.pdf")
                    .objectKey("key")
                    .contentType("application/pdf")
                    .build();

            when(attachmentRepository.findByWikiPageId(1L)).thenReturn(List.of(attachment));
            when(attachmentRepository.findById(1L)).thenReturn(java.util.Optional.of(attachment));

            searchService.reindexPage(1L);

            verify(attachmentRepository).findByWikiPageId(1L);
        }
    }

    @Nested
    @DisplayName("Search Stats Tests")
    class SearchStatsTests {

        @Test
        @DisplayName("Should return search statistics")
        void shouldReturnSearchStats() {
            when(searchableContentRepository.count()).thenReturn(10L);
            when(searchableContentRepository.countSearchableDocuments()).thenReturn(8L);
            when(searchableContentRepository.findByExtractionStatus(ExtractionStatus.PENDING))
                    .thenReturn(List.of());
            when(searchableContentRepository.findByExtractionStatus(ExtractionStatus.FAILED))
                    .thenReturn(List.of());

            var stats = searchService.getSearchStats();

            assertThat(stats.totalDocuments()).isEqualTo(10);
            assertThat(stats.indexedDocuments()).isEqualTo(8);
            assertThat(stats.pendingDocuments()).isEqualTo(0);
            assertThat(stats.failedDocuments()).isEqualTo(0);
        }
    }
}