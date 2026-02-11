package com.wiki.service;

import com.wiki.model.WikiPage;
import com.wiki.repository.WikiPageRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for WikiPageService
 * Tests backlink parsing, hierarchy, and slug generation
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("WikiPage Service Tests")
class WikiPageServiceTest {

    @Mock
    private WikiPageRepository wikiPageRepository;

    @InjectMocks
    private WikiPageService wikiPageService;

    // ==================== Link Extraction Tests ====================

    @Nested
    @DisplayName("Link Extraction Tests")
    class LinkExtractionTests {

        @Test
        @DisplayName("Should extract simple wiki link [[PageName]]")
        void shouldExtractSimpleWikiLink() {
            // Given
            String content = "This is a page with a [[TestPage]] link.";

            // When
            Set<String> links = wikiPageService.extractLinkedPageNames(content);

            // Then
            assertThat(links).containsExactly("TestPage");
        }

        @Test
        @DisplayName("Should extract wiki link with display text [[PageName|Display Text]]")
        void shouldExtractWikiLinkWithDisplayText() {
            // Given
            String content = "See [[Getting Started|the getting started guide]] for more info.";

            // When
            Set<String> links = wikiPageService.extractLinkedPageNames(content);

            // Then
            assertThat(links).containsExactly("Getting Started");
        }

        @Test
        @DisplayName("Should extract multiple wiki links")
        void shouldExtractMultipleWikiLinks() {
            // Given
            String content = """
                    Welcome to the Wiki!

                    Check out these pages:
                    - [[Introduction]]
                    - [[Getting Started|Quick Start Guide]]
                    - [[API Reference]]
                    - [[FAQ]]
                    """;

            // When
            Set<String> links = wikiPageService.extractLinkedPageNames(content);

            // Then
            assertThat(links)
                    .hasSize(4)
                    .containsExactlyInAnyOrder("Introduction", "Getting Started", "API Reference", "FAQ");
        }

        @Test
        @DisplayName("Should handle duplicate links")
        void shouldHandleDuplicateLinks() {
            // Given
            String content = "See [[Page1]] and later [[Page1]] again, plus [[Page2]].";

            // When
            Set<String> links = wikiPageService.extractLinkedPageNames(content);

            // Then
            assertThat(links)
                    .hasSize(2)
                    .containsExactlyInAnyOrder("Page1", "Page2");
        }

        @Test
        @DisplayName("Should return empty set for content without links")
        void shouldReturnEmptySetForContentWithoutLinks() {
            // Given
            String content = "This is plain text without any wiki links.";

            // When
            Set<String> links = wikiPageService.extractLinkedPageNames(content);

            // Then
            assertThat(links).isEmpty();
        }

        @Test
        @DisplayName("Should return empty set for null content")
        void shouldReturnEmptySetForNullContent() {
            // When
            Set<String> links = wikiPageService.extractLinkedPageNames(null);

            // Then
            assertThat(links).isEmpty();
        }

        @Test
        @DisplayName("Should handle links with spaces")
        void shouldHandleLinksWithSpaces() {
            // Given
            String content = "Check [[My Page Name]] and [[  Another Page  ]]";

            // When
            Set<String> links = wikiPageService.extractLinkedPageNames(content);

            // Then
            assertThat(links)
                    .hasSize(2)
                    .containsExactlyInAnyOrder("My Page Name", "Another Page");
        }

        @Test
        @DisplayName("Should not match incomplete brackets")
        void shouldNotMatchIncompleteBrackets() {
            // Given
            String content = "This has [single brackets] and [[incomplete and broken]] text";

            // When
            Set<String> links = wikiPageService.extractLinkedPageNames(content);

            // Then
            assertThat(links).containsExactly("incomplete and broken");
        }
    }

    // ==================== Slug Generation Tests ====================

    @Nested
    @DisplayName("Slug Generation Tests")
    class SlugGenerationTests {

        @Test
        @DisplayName("Should generate slug from simple title")
        void shouldGenerateSlugFromSimpleTitle() {
            // When
            String slug = wikiPageService.generateSlug("Hello World");

            // Then
            assertThat(slug).isEqualTo("hello-world");
        }

        @Test
        @DisplayName("Should handle special characters in title")
        void shouldHandleSpecialCharactersInTitle() {
            // When
            String slug = wikiPageService.generateSlug("What's New? (2024)");

            // Then
            assertThat(slug).isEqualTo("whats-new-2024");
        }

        @Test
        @DisplayName("Should handle unicode characters")
        void shouldHandleUnicodeCharacters() {
            // When
            String slug = wikiPageService.generateSlug("Café résumé");

            // Then
            assertThat(slug).isEqualTo("cafe-resume");
        }

        @Test
        @DisplayName("Should collapse multiple spaces and hyphens")
        void shouldCollapseMultipleSpacesAndHyphens() {
            // When
            String slug = wikiPageService.generateSlug("Hello   --  World");

            // Then
            assertThat(slug).isEqualTo("hello-world");
        }

        @Test
        @DisplayName("Should return 'page' for empty title")
        void shouldReturnPageForEmptyTitle() {
            // When
            String slug = wikiPageService.generateSlug("");

            // Then
            assertThat(slug).isEqualTo("");
        }

        @Test
        @DisplayName("Should generate unique slug when duplicate exists")
        void shouldGenerateUniqueSlugWhenDuplicateExists() {
            // Given
            when(wikiPageRepository.existsBySlug("hello-world")).thenReturn(true);
            when(wikiPageRepository.existsBySlug("hello-world-1")).thenReturn(true);
            when(wikiPageRepository.existsBySlug("hello-world-2")).thenReturn(false);

            // When
            String slug = wikiPageService.generateUniqueSlug("Hello World");

            // Then
            assertThat(slug).isEqualTo("hello-world-2");
        }
    }

    // ==================== Save Page Tests ====================

    @Nested
    @DisplayName("Save Page Tests")
    class SavePageTests {

        @Test
        @DisplayName("Should save page and generate slug")
        void shouldSavePageAndGenerateSlug() {
            // Given
            WikiPage page = WikiPage.builder()
                    .title("My New Page")
                    .content("Some content")
                    .build();

            when(wikiPageRepository.existsBySlug(any())).thenReturn(false);
            when(wikiPageRepository.findByTitle("My New Page")).thenReturn(Optional.empty());
            when(wikiPageRepository.save(any(WikiPage.class))).thenAnswer(inv -> {
                WikiPage saved = inv.getArgument(0);
                saved.setId(1L);
                return saved;
            });

            // When
            WikiPage savedPage = wikiPageService.savePage(page);

            // Then
            assertThat(savedPage.getSlug()).isEqualTo("my-new-page");
            verify(wikiPageRepository).save(page);
        }

        @Test
        @DisplayName("Should update backlinks when saving page with links")
        void shouldUpdateBacklinksWhenSavingPageWithLinks() {
            // Given
            WikiPage targetPage = WikiPage.builder()
                    .id(2L)
                    .title("Target Page")
                    .slug("target-page")
                    .build();

            WikiPage page = WikiPage.builder()
                    .title("My Page")
                    .content("This links to [[Target Page]]")
                    .build();

            when(wikiPageRepository.existsBySlug(any())).thenReturn(false);
            when(wikiPageRepository.findByTitle("My Page")).thenReturn(Optional.empty());
            when(wikiPageRepository.findByTitle("Target Page")).thenReturn(Optional.of(targetPage));
            when(wikiPageRepository.save(any(WikiPage.class))).thenAnswer(inv -> {
                WikiPage saved = inv.getArgument(0);
                saved.setId(1L);
                return saved;
            });

            // When
            WikiPage savedPage = wikiPageService.savePage(page);

            // Then
            assertThat(savedPage.getLinksTo()).contains(targetPage);
        }

        @Test
        @DisplayName("Should increment version on update")
        void shouldIncrementVersionOnUpdate() {
            // Given
            WikiPage existingPage = WikiPage.builder()
                    .id(1L)
                    .title("Existing Page")
                    .slug("existing-page")
                    .version(3)
                    .build();

            when(wikiPageRepository.findByTitle("Existing Page")).thenReturn(Optional.of(existingPage));
            when(wikiPageRepository.save(any(WikiPage.class))).thenAnswer(inv -> inv.getArgument(0));

            // When
            WikiPage savedPage = wikiPageService.savePage(existingPage);

            // Then
            assertThat(savedPage.getVersion()).isEqualTo(4);
        }

        @Test
        @DisplayName("Should throw exception for page without title")
        void shouldThrowExceptionForPageWithoutTitle() {
            // Given
            WikiPage page = WikiPage.builder()
                    .content("Some content")
                    .build();

            // When/Then
            assertThatThrownBy(() -> wikiPageService.savePage(page))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("title");
        }

        @Test
        @DisplayName("Should throw exception for duplicate title")
        void shouldThrowExceptionForDuplicateTitle() {
            // Given
            WikiPage existingPage = WikiPage.builder()
                    .id(1L)
                    .title("Existing Page")
                    .build();

            WikiPage newPage = WikiPage.builder()
                    .title("Existing Page")
                    .content("Different content")
                    .build();

            when(wikiPageRepository.findByTitle("Existing Page")).thenReturn(Optional.of(existingPage));

            // When/Then
            assertThatThrownBy(() -> wikiPageService.savePage(newPage))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("already exists");
        }
    }

    // ==================== Hierarchy Tests ====================

    @Nested
    @DisplayName("Hierarchy Tests")
    class HierarchyTests {

        @Test
        @DisplayName("Should save page with parent")
        void shouldSavePageWithParent() {
            // Given
            WikiPage parent = WikiPage.builder()
                    .id(1L)
                    .title("Parent Page")
                    .slug("parent-page")
                    .build();

            WikiPage child = WikiPage.builder()
                    .title("Child Page")
                    .content("Child content")
                    .build();

            when(wikiPageRepository.findById(1L)).thenReturn(Optional.of(parent));
            when(wikiPageRepository.existsBySlug(any())).thenReturn(false);
            when(wikiPageRepository.findByTitle("Child Page")).thenReturn(Optional.empty());
            when(wikiPageRepository.save(any(WikiPage.class))).thenAnswer(inv -> {
                WikiPage saved = inv.getArgument(0);
                saved.setId(2L);
                return saved;
            });

            // When
            WikiPage savedPage = wikiPageService.savePage(child, 1L);

            // Then
            assertThat(savedPage.getParent()).isEqualTo(parent);
        }

        @Test
        @DisplayName("Should throw exception when parent not found")
        void shouldThrowExceptionWhenParentNotFound() {
            // Given
            WikiPage child = WikiPage.builder()
                    .title("Child Page")
                    .build();

            when(wikiPageRepository.findById(999L)).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> wikiPageService.savePage(child, 999L))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Parent page not found");
        }

        @Test
        @DisplayName("Should prevent circular reference when moving page")
        void shouldPreventCircularReferenceWhenMovingPage() {
            // Given
            WikiPage parent = WikiPage.builder()
                    .id(1L)
                    .title("Parent")
                    .build();

            WikiPage child = WikiPage.builder()
                    .id(2L)
                    .title("Child")
                    .parent(parent)
                    .build();

            when(wikiPageRepository.findById(1L)).thenReturn(Optional.of(parent));
            when(wikiPageRepository.findById(2L)).thenReturn(Optional.of(child));

            // When/Then - Try to make parent a child of its own child
            assertThatThrownBy(() -> wikiPageService.movePage(1L, 2L))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("descendant");
        }

        @Test
        @DisplayName("Should prevent page from being its own parent")
        void shouldPreventPageFromBeingItsOwnParent() {
            // Given
            WikiPage page = WikiPage.builder()
                    .id(1L)
                    .title("Page")
                    .build();

            when(wikiPageRepository.findById(1L)).thenReturn(Optional.of(page));

            // When/Then
            assertThatThrownBy(() -> wikiPageService.movePage(1L, 1L))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("cannot be its own parent");
        }
    }

    // ==================== Broken Links Tests ====================

    @Nested
    @DisplayName("Broken Links Tests")
    class BrokenLinksTests {

        @Test
        @DisplayName("Should find broken links in page content")
        void shouldFindBrokenLinksInPageContent() {
            // Given
            WikiPage existingPage = WikiPage.builder()
                    .id(1L)
                    .title("Existing Page")
                    .build();

            WikiPage pageWithLinks = WikiPage.builder()
                    .title("Page with Links")
                    .content("Links to [[Existing Page]] and [[Non Existent Page]]")
                    .build();

            when(wikiPageRepository.findByTitle("Existing Page")).thenReturn(Optional.of(existingPage));
            when(wikiPageRepository.findByTitle("Non Existent Page")).thenReturn(Optional.empty());
            when(wikiPageRepository.findBySlug("non-existent-page")).thenReturn(Optional.empty());

            // When
            var brokenLinks = wikiPageService.findBrokenLinks(pageWithLinks);

            // Then
            assertThat(brokenLinks)
                    .hasSize(1)
                    .contains("Non Existent Page");
        }

        @Test
        @DisplayName("Should return empty list when no broken links")
        void shouldReturnEmptyListWhenNoBrokenLinks() {
            // Given
            WikiPage targetPage = WikiPage.builder()
                    .id(1L)
                    .title("Target Page")
                    .build();

            WikiPage pageWithLinks = WikiPage.builder()
                    .title("Page with Links")
                    .content("Links to [[Target Page]]")
                    .build();

            when(wikiPageRepository.findByTitle("Target Page")).thenReturn(Optional.of(targetPage));

            // When
            var brokenLinks = wikiPageService.findBrokenLinks(pageWithLinks);

            // Then
            assertThat(brokenLinks).isEmpty();
        }
    }
}