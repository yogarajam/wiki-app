package com.wiki.repository;

import com.wiki.model.WikiPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class WikiPageRepositoryIntegrationTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private WikiPageRepository repository;

    private WikiPage rootPage;
    private WikiPage childPage;
    private WikiPage grandchildPage;

    @BeforeEach
    void setUp() {
        rootPage = WikiPage.builder()
                .title("Root Page")
                .slug("root-page")
                .content("Root content with [[Child Page]] link")
                .published(true)
                .version(1)
                .build();
        entityManager.persistAndFlush(rootPage);

        childPage = WikiPage.builder()
                .title("Child Page")
                .slug("child-page")
                .content("Child content")
                .published(true)
                .version(1)
                .parent(rootPage)
                .build();
        entityManager.persistAndFlush(childPage);

        grandchildPage = WikiPage.builder()
                .title("Grandchild Page")
                .slug("grandchild-page")
                .content("Grandchild content")
                .published(false)
                .version(1)
                .parent(childPage)
                .build();
        entityManager.persistAndFlush(grandchildPage);

        entityManager.clear();
    }

    // ==================== Basic CRUD ====================

    @Test
    void shouldSaveAndFindById() {
        Optional<WikiPage> found = repository.findById(rootPage.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getTitle()).isEqualTo("Root Page");
        assertThat(found.get().getSlug()).isEqualTo("root-page");
    }

    @Test
    void shouldFindBySlug() {
        Optional<WikiPage> found = repository.findBySlug("child-page");

        assertThat(found).isPresent();
        assertThat(found.get().getTitle()).isEqualTo("Child Page");
    }

    @Test
    void shouldFindByTitle() {
        Optional<WikiPage> found = repository.findByTitle("Root Page");

        assertThat(found).isPresent();
        assertThat(found.get().getSlug()).isEqualTo("root-page");
    }

    @Test
    void shouldCheckExistsBySlug() {
        assertThat(repository.existsBySlug("root-page")).isTrue();
        assertThat(repository.existsBySlug("nonexistent")).isFalse();
    }

    @Test
    void shouldCheckExistsByTitle() {
        assertThat(repository.existsByTitle("Root Page")).isTrue();
        assertThat(repository.existsByTitle("Nonexistent")).isFalse();
    }

    @Test
    void shouldFindByTitleContainingIgnoreCase() {
        List<WikiPage> results = repository.findByTitleContainingIgnoreCase("child");

        assertThat(results).hasSize(2);
        assertThat(results).extracting(WikiPage::getTitle)
                .containsExactlyInAnyOrder("Child Page", "Grandchild Page");
    }

    // ==================== Hierarchy Queries ====================

    @Test
    void shouldFindAllRootPages() {
        List<WikiPage> roots = repository.findAllRootPages();

        assertThat(roots).hasSize(1);
        assertThat(roots.get(0).getTitle()).isEqualTo("Root Page");
    }

    @Test
    void shouldFindChildrenByParentId() {
        List<WikiPage> children = repository.findChildrenByParentId(rootPage.getId());

        assertThat(children).hasSize(1);
        assertThat(children.get(0).getTitle()).isEqualTo("Child Page");
    }

    @Test
    void shouldCountChildrenByParentId() {
        long count = repository.countChildrenByParentId(rootPage.getId());
        assertThat(count).isEqualTo(1);

        long childCount = repository.countChildrenByParentId(childPage.getId());
        assertThat(childCount).isEqualTo(1);

        long grandchildCount = repository.countChildrenByParentId(grandchildPage.getId());
        assertThat(grandchildCount).isZero();
    }

    @Test
    @Disabled("Recursive CTE with computed 'depth' column requires PostgreSQL - H2 incompatible")
    void shouldFindAllDescendantsNative() {
        List<Object[]> descendants = repository.findAllDescendantsNative(rootPage.getId());

        assertThat(descendants).hasSize(2);
    }

    @Test
    @Disabled("Recursive CTE with computed 'depth' column requires PostgreSQL - H2 incompatible")
    void shouldFindAllAncestorsNative() {
        List<Object[]> ancestors = repository.findAllAncestorsNative(grandchildPage.getId());

        assertThat(ancestors).hasSize(2);
    }

    // ==================== Backlink Queries ====================

    @Test
    void shouldFindPagesLinkingTo() {
        // Set up backlinks: rootPage links to childPage
        WikiPage source = entityManager.find(WikiPage.class, rootPage.getId());
        WikiPage target = entityManager.find(WikiPage.class, childPage.getId());
        source.addLinkTo(target);
        entityManager.persistAndFlush(source);
        entityManager.clear();

        List<WikiPage> linking = repository.findPagesLinkingTo(childPage.getId());

        assertThat(linking).hasSize(1);
        assertThat(linking.get(0).getTitle()).isEqualTo("Root Page");
    }

    @Test
    void shouldFindPagesLinkedFrom() {
        // Set up links: rootPage links to childPage and grandchildPage
        WikiPage source = entityManager.find(WikiPage.class, rootPage.getId());
        WikiPage target1 = entityManager.find(WikiPage.class, childPage.getId());
        WikiPage target2 = entityManager.find(WikiPage.class, grandchildPage.getId());
        source.addLinkTo(target1);
        source.addLinkTo(target2);
        entityManager.persistAndFlush(source);
        entityManager.clear();

        Set<WikiPage> linked = repository.findPagesLinkedFrom(rootPage.getId());

        assertThat(linked).hasSize(2);
    }

    @Test
    void shouldCountBacklinks() {
        WikiPage source = entityManager.find(WikiPage.class, rootPage.getId());
        WikiPage target = entityManager.find(WikiPage.class, childPage.getId());
        source.addLinkTo(target);
        entityManager.persistAndFlush(source);
        entityManager.clear();

        long count = repository.countBacklinks(childPage.getId());
        assertThat(count).isEqualTo(1);

        long noBacklinks = repository.countBacklinks(rootPage.getId());
        assertThat(noBacklinks).isZero();
    }

    @Test
    void shouldFindOrphanPages() {
        // rootPage is a root page with no backlinks, so it's an orphan
        // childPage has rootPage as parent, so it's not an orphan
        List<WikiPage> orphans = repository.findOrphanPages();

        assertThat(orphans).hasSize(1);
        assertThat(orphans.get(0).getTitle()).isEqualTo("Root Page");
    }

    // ==================== Search Queries ====================

    @Test
    void shouldSearchByTitleOrContent() {
        List<WikiPage> results = repository.searchByTitleOrContent("Root");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getTitle()).isEqualTo("Root Page");
    }

    @Test
    void shouldSearchByContent() {
        List<WikiPage> results = repository.searchByTitleOrContent("Grandchild content");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getTitle()).isEqualTo("Grandchild Page");
    }

    @Test
    void shouldFindByPublishedTrueOrderByTitleAsc() {
        List<WikiPage> published = repository.findByPublishedTrueOrderByTitleAsc();

        assertThat(published).hasSize(2);
        assertThat(published).extracting(WikiPage::getTitle)
                .containsExactly("Child Page", "Root Page");
    }

    @Test
    void shouldFindRecentlyUpdated() {
        List<WikiPage> recent = repository.findRecentlyUpdated();

        assertThat(recent).hasSize(3);
    }

    // ==================== Slug Queries ====================

    @Test
    void shouldFindSlugsByPattern() {
        List<String> slugs = repository.findSlugsByPattern("child%");

        assertThat(slugs).hasSize(1);
        assertThat(slugs).contains("child-page");
    }

    // ==================== Edge Cases ====================

    @Test
    void shouldReturnEmptyForNonexistentSlug() {
        Optional<WikiPage> found = repository.findBySlug("does-not-exist");
        assertThat(found).isEmpty();
    }

    @Test
    void shouldReturnEmptyChildrenForLeafPage() {
        List<WikiPage> children = repository.findChildrenByParentId(grandchildPage.getId());
        assertThat(children).isEmpty();
    }

    @Test
    @Disabled("Recursive CTE with computed 'depth' column requires PostgreSQL - H2 incompatible")
    void shouldReturnEmptyDescendantsForLeafPage() {
        List<Object[]> descendants = repository.findAllDescendantsNative(grandchildPage.getId());
        assertThat(descendants).isEmpty();
    }

    @Test
    @Disabled("Recursive CTE with computed 'depth' column requires PostgreSQL - H2 incompatible")
    void shouldReturnEmptyAncestorsForRootPage() {
        List<Object[]> ancestors = repository.findAllAncestorsNative(rootPage.getId());
        assertThat(ancestors).isEmpty();
    }
}
