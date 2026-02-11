package com.wiki.service;

import com.wiki.model.*;
import com.wiki.model.PagePermission.PermissionType;
import com.wiki.repository.PagePermissionRepository;
import com.wiki.repository.RoleRepository;
import com.wiki.repository.UserRepository;
import com.wiki.repository.WikiPageRepository;
import com.wiki.security.SecurityValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PageSecurityService Tests")
class PageSecurityServiceTest {

    @Mock
    private PagePermissionRepository permissionRepository;

    @Mock
    private WikiPageRepository wikiPageRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private SecurityValidator securityValidator;

    @InjectMocks
    private PageSecurityService pageSecurityService;

    private WikiPage testPage;
    private User adminUser;
    private User editorUser;
    private Role adminRole;
    private Role editorRole;

    @BeforeEach
    void setUp() {
        testPage = WikiPage.builder()
                .id(1L)
                .title("Test Page")
                .slug("test-page")
                .build();

        adminRole = Role.builder().id(1L).name(Role.ADMIN).description("Admin").build();
        editorRole = Role.builder().id(2L).name(Role.EDITOR).description("Editor").build();

        adminUser = User.builder()
                .id(1L)
                .username("admin")
                .email("admin@test.com")
                .password("pass")
                .roles(Set.of(adminRole))
                .build();

        editorUser = User.builder()
                .id(2L)
                .username("editor")
                .email("editor@test.com")
                .password("pass")
                .roles(Set.of(editorRole))
                .build();
    }

    // ==================== Grant User Permission Tests ====================

    @Nested
    @DisplayName("Grant User Permission Tests")
    class GrantUserPermissionTests {

        @Test
        @DisplayName("Should create new user permission when none exists")
        void shouldCreateNewUserPermission() {
            when(securityValidator.isAdmin()).thenReturn(true);
            when(wikiPageRepository.findById(1L)).thenReturn(Optional.of(testPage));
            when(userRepository.findById(2L)).thenReturn(Optional.of(editorUser));
            when(securityValidator.getCurrentUser()).thenReturn(Optional.of(adminUser));
            when(permissionRepository.findUserPermission(1L, 2L, PermissionType.EDIT))
                    .thenReturn(Optional.empty());
            when(permissionRepository.save(any(PagePermission.class)))
                    .thenAnswer(inv -> {
                        PagePermission p = inv.getArgument(0);
                        p.setId(10L);
                        return p;
                    });

            PagePermission result = pageSecurityService.grantUserPermission(1L, 2L, PermissionType.EDIT);

            assertThat(result.getWikiPage()).isEqualTo(testPage);
            assertThat(result.getUser()).isEqualTo(editorUser);
            assertThat(result.getPermissionType()).isEqualTo(PermissionType.EDIT);
            assertThat(result.isGranted()).isTrue();
            assertThat(result.getGrantedBy()).isEqualTo(adminUser);

            verify(permissionRepository).save(any(PagePermission.class));
        }

        @Test
        @DisplayName("Should update existing user permission")
        void shouldUpdateExistingUserPermission() {
            PagePermission existingPermission = PagePermission.builder()
                    .id(5L)
                    .wikiPage(testPage)
                    .user(editorUser)
                    .permissionType(PermissionType.EDIT)
                    .granted(false)
                    .build();

            when(securityValidator.isAdmin()).thenReturn(true);
            when(wikiPageRepository.findById(1L)).thenReturn(Optional.of(testPage));
            when(userRepository.findById(2L)).thenReturn(Optional.of(editorUser));
            when(securityValidator.getCurrentUser()).thenReturn(Optional.of(adminUser));
            when(permissionRepository.findUserPermission(1L, 2L, PermissionType.EDIT))
                    .thenReturn(Optional.of(existingPermission));
            when(permissionRepository.save(any(PagePermission.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            PagePermission result = pageSecurityService.grantUserPermission(1L, 2L, PermissionType.EDIT);

            assertThat(result.isGranted()).isTrue();
            assertThat(result.getGrantedBy()).isEqualTo(adminUser);
        }

        @Test
        @DisplayName("Should throw when page not found")
        void shouldThrowWhenPageNotFound() {
            when(securityValidator.isAdmin()).thenReturn(true);
            when(wikiPageRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    pageSecurityService.grantUserPermission(999L, 2L, PermissionType.EDIT))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Page not found");
        }

        @Test
        @DisplayName("Should throw when user not found")
        void shouldThrowWhenUserNotFound() {
            when(securityValidator.isAdmin()).thenReturn(true);
            when(wikiPageRepository.findById(1L)).thenReturn(Optional.of(testPage));
            when(userRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    pageSecurityService.grantUserPermission(1L, 999L, PermissionType.EDIT))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("User not found");
        }

        @Test
        @DisplayName("Should throw when not authenticated")
        void shouldThrowWhenNotAuthenticated() {
            when(securityValidator.isAdmin()).thenReturn(true);
            when(wikiPageRepository.findById(1L)).thenReturn(Optional.of(testPage));
            when(userRepository.findById(2L)).thenReturn(Optional.of(editorUser));
            when(securityValidator.getCurrentUser()).thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    pageSecurityService.grantUserPermission(1L, 2L, PermissionType.EDIT))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("Not authenticated");
        }

        @Test
        @DisplayName("Should throw when non-admin without manage permission")
        void shouldThrowWhenNonAdminWithoutManagePermission() {
            when(securityValidator.isAdmin()).thenReturn(false);
            when(securityValidator.canManagePermissions(1L)).thenReturn(false);

            assertThatThrownBy(() ->
                    pageSecurityService.grantUserPermission(1L, 2L, PermissionType.EDIT))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("do not have permission");
        }

        @Test
        @DisplayName("Should allow non-admin with manage permission to grant")
        void shouldAllowNonAdminWithManagePermission() {
            when(securityValidator.isAdmin()).thenReturn(false);
            when(securityValidator.canManagePermissions(1L)).thenReturn(true);
            when(wikiPageRepository.findById(1L)).thenReturn(Optional.of(testPage));
            when(userRepository.findById(2L)).thenReturn(Optional.of(editorUser));
            when(securityValidator.getCurrentUser()).thenReturn(Optional.of(editorUser));
            when(permissionRepository.findUserPermission(1L, 2L, PermissionType.VIEW))
                    .thenReturn(Optional.empty());
            when(permissionRepository.save(any(PagePermission.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            PagePermission result = pageSecurityService.grantUserPermission(1L, 2L, PermissionType.VIEW);

            assertThat(result).isNotNull();
            assertThat(result.getPermissionType()).isEqualTo(PermissionType.VIEW);
        }
    }

    // ==================== Grant Role Permission Tests ====================

    @Nested
    @DisplayName("Grant Role Permission Tests")
    class GrantRolePermissionTests {

        @Test
        @DisplayName("Should create new role permission")
        void shouldCreateNewRolePermission() {
            when(securityValidator.isAdmin()).thenReturn(true);
            when(wikiPageRepository.findById(1L)).thenReturn(Optional.of(testPage));
            when(roleRepository.findById(2L)).thenReturn(Optional.of(editorRole));
            when(securityValidator.getCurrentUser()).thenReturn(Optional.of(adminUser));
            when(permissionRepository.findRolePermission(1L, 2L, PermissionType.VIEW))
                    .thenReturn(Optional.empty());
            when(permissionRepository.save(any(PagePermission.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            PagePermission result = pageSecurityService.grantRolePermission(1L, 2L, PermissionType.VIEW);

            assertThat(result.getRole()).isEqualTo(editorRole);
            assertThat(result.getPermissionType()).isEqualTo(PermissionType.VIEW);
            assertThat(result.isGranted()).isTrue();
        }

        @Test
        @DisplayName("Should update existing role permission")
        void shouldUpdateExistingRolePermission() {
            PagePermission existing = PagePermission.builder()
                    .id(5L)
                    .wikiPage(testPage)
                    .role(editorRole)
                    .permissionType(PermissionType.VIEW)
                    .granted(false)
                    .build();

            when(securityValidator.isAdmin()).thenReturn(true);
            when(wikiPageRepository.findById(1L)).thenReturn(Optional.of(testPage));
            when(roleRepository.findById(2L)).thenReturn(Optional.of(editorRole));
            when(securityValidator.getCurrentUser()).thenReturn(Optional.of(adminUser));
            when(permissionRepository.findRolePermission(1L, 2L, PermissionType.VIEW))
                    .thenReturn(Optional.of(existing));
            when(permissionRepository.save(any(PagePermission.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            PagePermission result = pageSecurityService.grantRolePermission(1L, 2L, PermissionType.VIEW);

            assertThat(result.isGranted()).isTrue();
            assertThat(result.getGrantedBy()).isEqualTo(adminUser);
        }

        @Test
        @DisplayName("Should throw when role not found")
        void shouldThrowWhenRoleNotFound() {
            when(securityValidator.isAdmin()).thenReturn(true);
            when(wikiPageRepository.findById(1L)).thenReturn(Optional.of(testPage));
            when(roleRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    pageSecurityService.grantRolePermission(1L, 999L, PermissionType.VIEW))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Role not found");
        }
    }

    // ==================== Revoke Permission Tests ====================

    @Nested
    @DisplayName("Revoke User Permission Tests")
    class RevokeUserPermissionTests {

        @Test
        @DisplayName("Should revoke existing user permission")
        void shouldRevokeExistingUserPermission() {
            PagePermission existing = PagePermission.builder()
                    .id(5L)
                    .wikiPage(testPage)
                    .user(editorUser)
                    .permissionType(PermissionType.EDIT)
                    .build();

            when(securityValidator.isAdmin()).thenReturn(true);
            when(permissionRepository.findUserPermission(1L, 2L, PermissionType.EDIT))
                    .thenReturn(Optional.of(existing));

            pageSecurityService.revokeUserPermission(1L, 2L, PermissionType.EDIT);

            verify(permissionRepository).delete(existing);
        }

        @Test
        @DisplayName("Should do nothing when user permission does not exist")
        void shouldDoNothingWhenPermissionNotFound() {
            when(securityValidator.isAdmin()).thenReturn(true);
            when(permissionRepository.findUserPermission(1L, 2L, PermissionType.EDIT))
                    .thenReturn(Optional.empty());

            pageSecurityService.revokeUserPermission(1L, 2L, PermissionType.EDIT);

            verify(permissionRepository, never()).delete(any());
        }

        @Test
        @DisplayName("Should throw when non-admin tries to revoke")
        void shouldThrowWhenNonAdminTriesToRevoke() {
            when(securityValidator.isAdmin()).thenReturn(false);
            when(securityValidator.canManagePermissions(1L)).thenReturn(false);

            assertThatThrownBy(() ->
                    pageSecurityService.revokeUserPermission(1L, 2L, PermissionType.EDIT))
                    .isInstanceOf(AccessDeniedException.class);
        }
    }

    @Nested
    @DisplayName("Revoke Role Permission Tests")
    class RevokeRolePermissionTests {

        @Test
        @DisplayName("Should revoke existing role permission")
        void shouldRevokeExistingRolePermission() {
            PagePermission existing = PagePermission.builder()
                    .id(5L)
                    .wikiPage(testPage)
                    .role(editorRole)
                    .permissionType(PermissionType.VIEW)
                    .build();

            when(securityValidator.isAdmin()).thenReturn(true);
            when(permissionRepository.findRolePermission(1L, 2L, PermissionType.VIEW))
                    .thenReturn(Optional.of(existing));

            pageSecurityService.revokeRolePermission(1L, 2L, PermissionType.VIEW);

            verify(permissionRepository).delete(existing);
        }

        @Test
        @DisplayName("Should do nothing when role permission does not exist")
        void shouldDoNothingWhenRolePermissionNotFound() {
            when(securityValidator.isAdmin()).thenReturn(true);
            when(permissionRepository.findRolePermission(1L, 2L, PermissionType.VIEW))
                    .thenReturn(Optional.empty());

            pageSecurityService.revokeRolePermission(1L, 2L, PermissionType.VIEW);

            verify(permissionRepository, never()).delete(any());
        }
    }

    @Nested
    @DisplayName("Revoke All Permissions Tests")
    class RevokeAllPermissionsTests {

        @Test
        @DisplayName("Should revoke all permissions for a page")
        void shouldRevokeAllPermissions() {
            when(securityValidator.isAdmin()).thenReturn(true);

            pageSecurityService.revokeAllPermissions(1L);

            verify(permissionRepository).deleteByWikiPageId(1L);
        }

        @Test
        @DisplayName("Should throw when non-admin tries to revoke all")
        void shouldThrowWhenNonAdminTriesToRevokeAll() {
            when(securityValidator.isAdmin()).thenReturn(false);
            when(securityValidator.canManagePermissions(1L)).thenReturn(false);

            assertThatThrownBy(() -> pageSecurityService.revokeAllPermissions(1L))
                    .isInstanceOf(AccessDeniedException.class);
        }
    }

    // ==================== Mark as Sensitive/Public Tests ====================

    @Nested
    @DisplayName("Mark as Sensitive Tests")
    class MarkAsSensitiveTests {

        @Test
        @DisplayName("Should mark page as sensitive with admin full access")
        void shouldMarkPageAsSensitive() {
            when(securityValidator.isAdmin()).thenReturn(true);
            when(wikiPageRepository.findById(1L)).thenReturn(Optional.of(testPage));
            when(securityValidator.getCurrentUser()).thenReturn(Optional.of(adminUser));
            when(permissionRepository.countByWikiPageId(1L)).thenReturn(0L);
            when(roleRepository.findByName(Role.ADMIN)).thenReturn(Optional.of(adminRole));
            when(permissionRepository.save(any(PagePermission.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            pageSecurityService.markAsSensitive(1L);

            ArgumentCaptor<PagePermission> captor = ArgumentCaptor.forClass(PagePermission.class);
            verify(permissionRepository).save(captor.capture());

            PagePermission saved = captor.getValue();
            assertThat(saved.getRole()).isEqualTo(adminRole);
            assertThat(saved.getPermissionType()).isEqualTo(PermissionType.FULL_ACCESS);
            assertThat(saved.isGranted()).isTrue();
        }

        @Test
        @DisplayName("Should skip marking if page already has permissions")
        void shouldSkipIfAlreadySensitive() {
            when(securityValidator.isAdmin()).thenReturn(true);
            when(wikiPageRepository.findById(1L)).thenReturn(Optional.of(testPage));
            when(securityValidator.getCurrentUser()).thenReturn(Optional.of(adminUser));
            when(permissionRepository.countByWikiPageId(1L)).thenReturn(3L);

            pageSecurityService.markAsSensitive(1L);

            verify(permissionRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should create admin role if it does not exist")
        void shouldCreateAdminRoleIfNotExists() {
            when(securityValidator.isAdmin()).thenReturn(true);
            when(wikiPageRepository.findById(1L)).thenReturn(Optional.of(testPage));
            when(securityValidator.getCurrentUser()).thenReturn(Optional.of(adminUser));
            when(permissionRepository.countByWikiPageId(1L)).thenReturn(0L);
            when(roleRepository.findByName(Role.ADMIN)).thenReturn(Optional.empty());
            when(roleRepository.save(any(Role.class))).thenAnswer(inv -> {
                Role r = inv.getArgument(0);
                r.setId(99L);
                return r;
            });
            when(permissionRepository.save(any(PagePermission.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            pageSecurityService.markAsSensitive(1L);

            verify(roleRepository).save(any(Role.class));
            verify(permissionRepository).save(any(PagePermission.class));
        }

        @Test
        @DisplayName("Should throw when non-admin tries to mark as sensitive")
        void shouldThrowWhenNonAdminTriesToMarkSensitive() {
            when(securityValidator.isAdmin()).thenReturn(false);

            assertThatThrownBy(() -> pageSecurityService.markAsSensitive(1L))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("Only administrators");
        }

        @Test
        @DisplayName("Should throw when page not found")
        void shouldThrowWhenPageNotFoundForSensitive() {
            when(securityValidator.isAdmin()).thenReturn(true);
            when(wikiPageRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> pageSecurityService.markAsSensitive(999L))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Page not found");
        }

        @Test
        @DisplayName("Should throw when admin not authenticated")
        void shouldThrowWhenAdminNotAuthenticated() {
            when(securityValidator.isAdmin()).thenReturn(true);
            when(wikiPageRepository.findById(1L)).thenReturn(Optional.of(testPage));
            when(securityValidator.getCurrentUser()).thenReturn(Optional.empty());

            assertThatThrownBy(() -> pageSecurityService.markAsSensitive(1L))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("Not authenticated");
        }
    }

    @Nested
    @DisplayName("Mark as Public Tests")
    class MarkAsPublicTests {

        @Test
        @DisplayName("Should mark page as public by revoking all permissions")
        void shouldMarkPageAsPublic() {
            when(securityValidator.isAdmin()).thenReturn(true);

            pageSecurityService.markAsPublic(1L);

            verify(permissionRepository).deleteByWikiPageId(1L);
        }

        @Test
        @DisplayName("Should throw when non-admin tries to mark as public")
        void shouldThrowWhenNonAdminTriesToMarkPublic() {
            when(securityValidator.isAdmin()).thenReturn(false);

            assertThatThrownBy(() -> pageSecurityService.markAsPublic(1L))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("Only administrators");
        }
    }

    // ==================== Query Operations Tests ====================

    @Nested
    @DisplayName("Query Operations Tests")
    class QueryOperationsTests {

        @Test
        @DisplayName("Should get page permissions")
        void shouldGetPagePermissions() {
            PagePermission perm = PagePermission.builder()
                    .id(1L)
                    .wikiPage(testPage)
                    .user(editorUser)
                    .permissionType(PermissionType.EDIT)
                    .build();
            when(permissionRepository.findByWikiPageId(1L)).thenReturn(List.of(perm));

            List<PagePermission> result = pageSecurityService.getPagePermissions(1L);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getPermissionType()).isEqualTo(PermissionType.EDIT);
        }

        @Test
        @DisplayName("Should get user permissions")
        void shouldGetUserPermissions() {
            when(permissionRepository.findByUserId(2L)).thenReturn(Collections.emptyList());

            List<PagePermission> result = pageSecurityService.getUserPermissions(2L);

            assertThat(result).isEmpty();
            verify(permissionRepository).findByUserId(2L);
        }

        @Test
        @DisplayName("Should check if page is sensitive")
        void shouldCheckIfPageIsSensitive() {
            when(permissionRepository.countByWikiPageId(1L)).thenReturn(3L);

            assertThat(pageSecurityService.isSensitive(1L)).isTrue();
        }

        @Test
        @DisplayName("Should return false for non-sensitive page")
        void shouldReturnFalseForNonSensitive() {
            when(permissionRepository.countByWikiPageId(1L)).thenReturn(0L);

            assertThat(pageSecurityService.isSensitive(1L)).isFalse();
        }

        @Test
        @DisplayName("Should get sensitive page IDs")
        void shouldGetSensitivePages() {
            when(permissionRepository.findPagesWithPermissions())
                    .thenReturn(Set.of(1L, 5L, 10L));

            Set<Long> result = pageSecurityService.getSensitivePages();

            assertThat(result).containsExactlyInAnyOrder(1L, 5L, 10L);
        }
    }

    // ==================== Get Users With Permission Tests ====================

    @Nested
    @DisplayName("Get Users With Permission Tests")
    class GetUsersWithPermissionTests {

        @Test
        @DisplayName("Should return users with specific permission")
        void shouldReturnUsersWithPermission() {
            PagePermission editPerm = PagePermission.builder()
                    .id(1L)
                    .wikiPage(testPage)
                    .user(editorUser)
                    .permissionType(PermissionType.EDIT)
                    .granted(true)
                    .build();

            when(permissionRepository.findByWikiPageId(1L)).thenReturn(List.of(editPerm));

            List<User> result = pageSecurityService.getUsersWithPermission(1L, PermissionType.EDIT);

            assertThat(result).hasSize(1);
            assertThat(result.get(0)).isEqualTo(editorUser);
        }

        @Test
        @DisplayName("Should include users with FULL_ACCESS when querying specific type")
        void shouldIncludeFullAccessUsers() {
            PagePermission fullAccessPerm = PagePermission.builder()
                    .id(1L)
                    .wikiPage(testPage)
                    .user(adminUser)
                    .permissionType(PermissionType.FULL_ACCESS)
                    .granted(true)
                    .build();

            when(permissionRepository.findByWikiPageId(1L)).thenReturn(List.of(fullAccessPerm));

            List<User> result = pageSecurityService.getUsersWithPermission(1L, PermissionType.EDIT);

            assertThat(result).hasSize(1);
            assertThat(result.get(0)).isEqualTo(adminUser);
        }

        @Test
        @DisplayName("Should exclude non-granted permissions")
        void shouldExcludeNonGrantedPermissions() {
            PagePermission deniedPerm = PagePermission.builder()
                    .id(1L)
                    .wikiPage(testPage)
                    .user(editorUser)
                    .permissionType(PermissionType.EDIT)
                    .granted(false)
                    .build();

            when(permissionRepository.findByWikiPageId(1L)).thenReturn(List.of(deniedPerm));

            List<User> result = pageSecurityService.getUsersWithPermission(1L, PermissionType.EDIT);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Should exclude role-based permissions from user list")
        void shouldExcludeRoleBasedPermissions() {
            PagePermission rolePerm = PagePermission.builder()
                    .id(1L)
                    .wikiPage(testPage)
                    .role(editorRole) // role-based, not user-based
                    .permissionType(PermissionType.EDIT)
                    .granted(true)
                    .build();

            when(permissionRepository.findByWikiPageId(1L)).thenReturn(List.of(rolePerm));

            List<User> result = pageSecurityService.getUsersWithPermission(1L, PermissionType.EDIT);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Should return empty when no matching permissions")
        void shouldReturnEmptyWhenNoMatching() {
            PagePermission viewPerm = PagePermission.builder()
                    .id(1L)
                    .wikiPage(testPage)
                    .user(editorUser)
                    .permissionType(PermissionType.VIEW)
                    .granted(true)
                    .build();

            when(permissionRepository.findByWikiPageId(1L)).thenReturn(List.of(viewPerm));

            List<User> result = pageSecurityService.getUsersWithPermission(1L, PermissionType.DELETE);

            assertThat(result).isEmpty();
        }
    }
}