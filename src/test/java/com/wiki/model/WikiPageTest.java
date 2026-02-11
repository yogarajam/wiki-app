package com.wiki.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("WikiPage Model Tests")
class WikiPageTest {

    private WikiPage page;

    @BeforeEach
    void setUp() {
        page = WikiPage.builder()
                .id(1L)
                .title("Test Page")
                .slug("test-page")
                .build();
    }

    @Nested
    @DisplayName("Child Management Tests")
    class ChildManagementTests {

        @Test
        @DisplayName("Should add child and set parent reference")
        void shouldAddChildAndSetParent() {
            WikiPage child = WikiPage.builder().id(2L).title("Child").build();

            page.addChild(child);

            assertThat(page.getChildren()).contains(child);
            assertThat(child.getParent()).isEqualTo(page);
        }

        @Test
        @DisplayName("Should remove child and clear parent reference")
        void shouldRemoveChildAndClearParent() {
            WikiPage child = WikiPage.builder().id(2L).title("Child").build();
            page.addChild(child);

            page.removeChild(child);

            assertThat(page.getChildren()).doesNotContain(child);
            assertThat(child.getParent()).isNull();
        }

        @Test
        @DisplayName("hasChildren should return true when children exist")
        void hasChildrenShouldReturnTrue() {
            WikiPage child = WikiPage.builder().id(2L).title("Child").build();
            page.addChild(child);

            assertThat(page.hasChildren()).isTrue();
        }

        @Test
        @DisplayName("hasChildren should return false when no children")
        void hasChildrenShouldReturnFalse() {
            assertThat(page.hasChildren()).isFalse();
        }
    }

    @Nested
    @DisplayName("Link Management Tests")
    class LinkManagementTests {

        @Test
        @DisplayName("Should add outgoing link")
        void shouldAddOutgoingLink() {
            WikiPage target = WikiPage.builder().id(2L).title("Target").build();

            page.addLinkTo(target);

            assertThat(page.getLinksTo()).contains(target);
        }

        @Test
        @DisplayName("Should remove outgoing link")
        void shouldRemoveOutgoingLink() {
            WikiPage target = WikiPage.builder().id(2L).title("Target").build();
            page.addLinkTo(target);

            page.removeLinkTo(target);

            assertThat(page.getLinksTo()).doesNotContain(target);
        }

        @Test
        @DisplayName("Should clear all outgoing links")
        void shouldClearAllOutgoingLinks() {
            page.addLinkTo(WikiPage.builder().id(2L).title("A").build());
            page.addLinkTo(WikiPage.builder().id(3L).title("B").build());

            page.clearLinksTo();

            assertThat(page.getLinksTo()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Attachment Management Tests")
    class AttachmentManagementTests {

        @Test
        @DisplayName("Should add attachment and set wiki page reference")
        void shouldAddAttachment() {
            Attachment attachment = Attachment.builder().id(1L).originalFilename("test.pdf").build();

            page.addAttachment(attachment);

            assertThat(page.getAttachments()).contains(attachment);
            assertThat(attachment.getWikiPage()).isEqualTo(page);
        }

        @Test
        @DisplayName("Should remove attachment and clear wiki page reference")
        void shouldRemoveAttachment() {
            Attachment attachment = Attachment.builder().id(1L).originalFilename("test.pdf").build();
            page.addAttachment(attachment);

            page.removeAttachment(attachment);

            assertThat(page.getAttachments()).doesNotContain(attachment);
            assertThat(attachment.getWikiPage()).isNull();
        }
    }

    @Nested
    @DisplayName("Hierarchy Navigation Tests")
    class HierarchyNavigationTests {

        @Test
        @DisplayName("Root page should have depth 0")
        void rootPageShouldHaveDepthZero() {
            assertThat(page.getDepth()).isEqualTo(0);
        }

        @Test
        @DisplayName("Child page should have depth 1")
        void childPageShouldHaveDepthOne() {
            WikiPage child = WikiPage.builder().id(2L).title("Child").build();
            page.addChild(child);

            assertThat(child.getDepth()).isEqualTo(1);
        }

        @Test
        @DisplayName("Grandchild page should have depth 2")
        void grandchildShouldHaveDepthTwo() {
            WikiPage child = WikiPage.builder().id(2L).title("Child").build();
            WikiPage grandchild = WikiPage.builder().id(3L).title("Grandchild").build();
            page.addChild(child);
            child.addChild(grandchild);

            assertThat(grandchild.getDepth()).isEqualTo(2);
        }

        @Test
        @DisplayName("isRoot should return true for page without parent")
        void isRootShouldReturnTrue() {
            assertThat(page.isRoot()).isTrue();
        }

        @Test
        @DisplayName("isRoot should return false for child page")
        void isRootShouldReturnFalse() {
            WikiPage child = WikiPage.builder().id(2L).title("Child").build();
            page.addChild(child);

            assertThat(child.isRoot()).isFalse();
        }

        @Test
        @DisplayName("getPath should return path from root to page")
        void getPathShouldReturnFullPath() {
            WikiPage child = WikiPage.builder().id(2L).title("Child").build();
            WikiPage grandchild = WikiPage.builder().id(3L).title("Grandchild").build();
            page.addChild(child);
            child.addChild(grandchild);

            var path = grandchild.getPath();

            assertThat(path).hasSize(3);
            assertThat(path.get(0)).isEqualTo(page);
            assertThat(path.get(1)).isEqualTo(child);
            assertThat(path.get(2)).isEqualTo(grandchild);
        }

        @Test
        @DisplayName("getPath for root page should contain only itself")
        void getPathForRootShouldContainOnlyItself() {
            var path = page.getPath();

            assertThat(path).hasSize(1).containsExactly(page);
        }
    }

    @Nested
    @DisplayName("Backlink Count Tests")
    class BacklinkCountTests {

        @Test
        @DisplayName("Should return 0 when no backlinks")
        void shouldReturnZeroWhenNoBacklinks() {
            assertThat(page.getBacklinkCount()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("Equals and HashCode Tests")
    class EqualsAndHashCodeTests {

        @Test
        @DisplayName("Pages with same ID should be equal")
        void pagesWithSameIdShouldBeEqual() {
            WikiPage page2 = WikiPage.builder().id(1L).title("Different Title").build();

            assertThat(page).isEqualTo(page2);
        }

        @Test
        @DisplayName("Pages with different IDs should not be equal")
        void pagesWithDifferentIdsShouldNotBeEqual() {
            WikiPage page2 = WikiPage.builder().id(2L).title("Test Page").build();

            assertThat(page).isNotEqualTo(page2);
        }

        @Test
        @DisplayName("Page should not equal null")
        void pageShouldNotEqualNull() {
            assertThat(page).isNotEqualTo(null);
        }

        @Test
        @DisplayName("Page with null ID should not equal page with ID")
        void pageWithNullIdShouldNotEqual() {
            WikiPage pageNoId = WikiPage.builder().title("Test").build();

            assertThat(pageNoId).isNotEqualTo(page);
        }
    }

    @Nested
    @DisplayName("Default Values Tests")
    class DefaultValuesTests {

        @Test
        @DisplayName("Should have default published = false")
        void shouldHaveDefaultPublishedFalse() {
            WikiPage newPage = WikiPage.builder().title("New").build();
            assertThat(newPage.getPublished()).isFalse();
        }

        @Test
        @DisplayName("Should have default version = 1")
        void shouldHaveDefaultVersionOne() {
            WikiPage newPage = WikiPage.builder().title("New").build();
            assertThat(newPage.getVersion()).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("toString should include id, title, and slug")
    void toStringShouldIncludeKeyFields() {
        String str = page.toString();
        assertThat(str).contains("1").contains("Test Page").contains("test-page");
    }
}