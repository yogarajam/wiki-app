package com.wiki.repository;

import com.wiki.model.*;
import com.wiki.model.PagePermission.PermissionType;
import org.junit.jupiter.api.BeforeEach;
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
class PagePermissionRepositoryIntegrationTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private PagePermissionRepository repository;

    private WikiPage page;
    private WikiPage page2;
    private User adminUser;
    private User editorUser;
    private Role adminRole;
    private Role editorRole;

    @BeforeEach
    void setUp() {
        page = WikiPage.builder()
                .title("Sensitive Page")
                .slug("sensitive-page")
                .content("Secret content")
                .published(false)
                .version(1)
                .build();
        entityManager.persistAndFlush(page);

        page2 = WikiPage.builder()
                .title("Public Page")
                .slug("public-page")
                .content("Public content")
                .published(true)
                .version(1)
                .build();
        entityManager.persistAndFlush(page2);

        adminRole = Role.builder()
                .name("ROLE_ADMIN")
                .description("Admin")
                .build();
        entityManager.persistAndFlush(adminRole);

        editorRole = Role.builder()
                .name("ROLE_EDITOR")
                .description("Editor")
                .build();
        entityManager.persistAndFlush(editorRole);

        adminUser = User.builder()
                .username("admin")
                .email("admin@test.com")
                .password("encoded")
                .displayName("Admin")
                .enabled(true)
                .accountNonLocked(true)
                .build();
        adminUser.addRole(adminRole);
        entityManager.persistAndFlush(adminUser);

        editorUser = User.builder()
                .username("editor")
                .email("editor@test.com")
                .password("encoded")
                .displayName("Editor")
                .enabled(true)
                .accountNonLocked(true)
                .build();
        editorUser.addRole(editorRole);
        entityManager.persistAndFlush(editorUser);

        entityManager.clear();
    }

    @Test
    void shouldFindUserPermission() {
        PagePermission perm = PagePermission.builder()
                .wikiPage(page)
                .user(adminUser)
                .permissionType(PermissionType.EDIT)
                .granted(true)
                .build();
        entityManager.persistAndFlush(perm);
        entityManager.clear();

        Optional<PagePermission> found = repository.findUserPermission(
                page.getId(), adminUser.getId(), PermissionType.EDIT);

        assertThat(found).isPresent();
        assertThat(found.get().getPermissionType()).isEqualTo(PermissionType.EDIT);
        assertThat(found.get().isGranted()).isTrue();
    }

    @Test
    void shouldReturnEmptyForNonexistentUserPermission() {
        Optional<PagePermission> found = repository.findUserPermission(
                page.getId(), adminUser.getId(), PermissionType.DELETE);

        assertThat(found).isEmpty();
    }

    @Test
    void shouldFindRolePermission() {
        PagePermission perm = PagePermission.builder()
                .wikiPage(page)
                .role(editorRole)
                .permissionType(PermissionType.VIEW)
                .granted(true)
                .build();
        entityManager.persistAndFlush(perm);
        entityManager.clear();

        Optional<PagePermission> found = repository.findRolePermission(
                page.getId(), editorRole.getId(), PermissionType.VIEW);

        assertThat(found).isPresent();
        assertThat(found.get().getPermissionType()).isEqualTo(PermissionType.VIEW);
    }

    @Test
    void shouldCheckHasUserPermission() {
        PagePermission perm = PagePermission.builder()
                .wikiPage(page)
                .user(editorUser)
                .permissionType(PermissionType.EDIT)
                .granted(true)
                .build();
        entityManager.persistAndFlush(perm);
        entityManager.clear();

        boolean hasEdit = repository.hasUserPermission(
                page.getId(), editorUser.getId(),
                Set.of(PermissionType.EDIT, PermissionType.FULL_ACCESS));
        assertThat(hasEdit).isTrue();

        boolean hasDelete = repository.hasUserPermission(
                page.getId(), editorUser.getId(),
                Set.of(PermissionType.DELETE));
        assertThat(hasDelete).isFalse();
    }

    @Test
    void shouldCheckHasRolePermission() {
        PagePermission perm = PagePermission.builder()
                .wikiPage(page)
                .role(adminRole)
                .permissionType(PermissionType.FULL_ACCESS)
                .granted(true)
                .build();
        entityManager.persistAndFlush(perm);
        entityManager.clear();

        boolean hasAccess = repository.hasRolePermission(
                page.getId(), Set.of(adminRole.getId()),
                Set.of(PermissionType.FULL_ACCESS));
        assertThat(hasAccess).isTrue();

        boolean editorHasAccess = repository.hasRolePermission(
                page.getId(), Set.of(editorRole.getId()),
                Set.of(PermissionType.FULL_ACCESS));
        assertThat(editorHasAccess).isFalse();
    }

    @Test
    void shouldFindGrantedUserPermissions() {
        PagePermission grantedPerm = PagePermission.builder()
                .wikiPage(page)
                .user(editorUser)
                .permissionType(PermissionType.VIEW)
                .granted(true)
                .build();
        entityManager.persistAndFlush(grantedPerm);

        PagePermission deniedPerm = PagePermission.builder()
                .wikiPage(page)
                .user(editorUser)
                .permissionType(PermissionType.DELETE)
                .granted(false)
                .build();
        entityManager.persistAndFlush(deniedPerm);
        entityManager.clear();

        List<PagePermission> granted = repository.findGrantedUserPermissions(
                page.getId(), editorUser.getId());

        assertThat(granted).hasSize(1);
        assertThat(granted.get(0).getPermissionType()).isEqualTo(PermissionType.VIEW);
    }

    @Test
    void shouldFindGrantedRolePermissions() {
        PagePermission perm = PagePermission.builder()
                .wikiPage(page)
                .role(editorRole)
                .permissionType(PermissionType.VIEW)
                .granted(true)
                .build();
        entityManager.persistAndFlush(perm);
        entityManager.clear();

        List<PagePermission> granted = repository.findGrantedRolePermissions(
                page.getId(), Set.of(editorRole.getId()));

        assertThat(granted).hasSize(1);
        assertThat(granted.get(0).getPermissionType()).isEqualTo(PermissionType.VIEW);
    }

    @Test
    void shouldFindPagesWithPermissions() {
        PagePermission perm = PagePermission.builder()
                .wikiPage(page)
                .role(adminRole)
                .permissionType(PermissionType.FULL_ACCESS)
                .granted(true)
                .build();
        entityManager.persistAndFlush(perm);
        entityManager.clear();

        Set<Long> sensitivePages = repository.findPagesWithPermissions();

        assertThat(sensitivePages).containsExactly(page.getId());
        assertThat(sensitivePages).doesNotContain(page2.getId());
    }

    @Test
    void shouldCountByWikiPageId() {
        PagePermission perm1 = PagePermission.builder()
                .wikiPage(page)
                .user(adminUser)
                .permissionType(PermissionType.EDIT)
                .granted(true)
                .build();
        entityManager.persistAndFlush(perm1);

        PagePermission perm2 = PagePermission.builder()
                .wikiPage(page)
                .role(editorRole)
                .permissionType(PermissionType.VIEW)
                .granted(true)
                .build();
        entityManager.persistAndFlush(perm2);
        entityManager.clear();

        long count = repository.countByWikiPageId(page.getId());
        assertThat(count).isEqualTo(2);

        long countPage2 = repository.countByWikiPageId(page2.getId());
        assertThat(countPage2).isZero();
    }

    @Test
    void shouldDeleteByWikiPageId() {
        PagePermission perm = PagePermission.builder()
                .wikiPage(page)
                .user(adminUser)
                .permissionType(PermissionType.FULL_ACCESS)
                .granted(true)
                .build();
        entityManager.persistAndFlush(perm);
        entityManager.clear();

        assertThat(repository.countByWikiPageId(page.getId())).isEqualTo(1);

        repository.deleteByWikiPageId(page.getId());
        entityManager.flush();
        entityManager.clear();

        assertThat(repository.countByWikiPageId(page.getId())).isZero();
    }

    @Test
    void shouldFindByWikiPageId() {
        PagePermission perm = PagePermission.builder()
                .wikiPage(page)
                .user(editorUser)
                .permissionType(PermissionType.VIEW)
                .granted(true)
                .build();
        entityManager.persistAndFlush(perm);
        entityManager.clear();

        List<PagePermission> perms = repository.findByWikiPageId(page.getId());

        assertThat(perms).hasSize(1);
    }

    @Test
    void shouldFindByUserId() {
        PagePermission perm1 = PagePermission.builder()
                .wikiPage(page)
                .user(editorUser)
                .permissionType(PermissionType.VIEW)
                .granted(true)
                .build();
        entityManager.persistAndFlush(perm1);

        PagePermission perm2 = PagePermission.builder()
                .wikiPage(page2)
                .user(editorUser)
                .permissionType(PermissionType.EDIT)
                .granted(true)
                .build();
        entityManager.persistAndFlush(perm2);
        entityManager.clear();

        List<PagePermission> perms = repository.findByUserId(editorUser.getId());

        assertThat(perms).hasSize(2);
    }

    @Test
    void shouldFindByRoleId() {
        PagePermission perm = PagePermission.builder()
                .wikiPage(page)
                .role(adminRole)
                .permissionType(PermissionType.FULL_ACCESS)
                .granted(true)
                .build();
        entityManager.persistAndFlush(perm);
        entityManager.clear();

        List<PagePermission> perms = repository.findByRoleId(adminRole.getId());

        assertThat(perms).hasSize(1);
    }
}
