package com.wiki.service;

import com.wiki.model.*;
import com.wiki.model.PagePermission.PermissionType;
import com.wiki.repository.PagePermissionRepository;
import com.wiki.repository.RoleRepository;
import com.wiki.repository.UserRepository;
import com.wiki.repository.WikiPageRepository;
import com.wiki.security.SecurityValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service for managing page-level security and permissions.
 * Allows administrators and authorized users to grant/revoke access to sensitive pages.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PageSecurityService {

    private final PagePermissionRepository permissionRepository;
    private final WikiPageRepository wikiPageRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final SecurityValidator securityValidator;

    // ==================== Grant Permissions ====================

    /**
     * Grant a user permission on a wiki page.
     *
     * @param pageId         the wiki page ID
     * @param userId         the user ID to grant permission to
     * @param permissionType the type of permission to grant
     * @return the created permission
     */
    @Transactional
    public PagePermission grantUserPermission(Long pageId, Long userId, PermissionType permissionType) {
        validateManagePermissionAccess(pageId);

        WikiPage page = wikiPageRepository.findById(pageId)
                .orElseThrow(() -> new IllegalArgumentException("Page not found: " + pageId));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        User grantedBy = securityValidator.getCurrentUser()
                .orElseThrow(() -> new AccessDeniedException("Not authenticated"));

        // Check if permission already exists
        Optional<PagePermission> existing = permissionRepository.findUserPermission(pageId, userId, permissionType);
        if (existing.isPresent()) {
            PagePermission permission = existing.get();
            permission.setGranted(true);
            permission.setGrantedBy(grantedBy);
            log.info("Updated permission {} for user {} on page {}",
                    permissionType, user.getUsername(), page.getTitle());
            return permissionRepository.save(permission);
        }

        // Create new permission
        PagePermission permission = PagePermission.builder()
                .wikiPage(page)
                .user(user)
                .permissionType(permissionType)
                .granted(true)
                .grantedBy(grantedBy)
                .build();

        log.info("Granted permission {} to user {} on page {}",
                permissionType, user.getUsername(), page.getTitle());

        return permissionRepository.save(permission);
    }

    /**
     * Grant a role permission on a wiki page.
     *
     * @param pageId         the wiki page ID
     * @param roleId         the role ID to grant permission to
     * @param permissionType the type of permission to grant
     * @return the created permission
     */
    @Transactional
    public PagePermission grantRolePermission(Long pageId, Long roleId, PermissionType permissionType) {
        validateManagePermissionAccess(pageId);

        WikiPage page = wikiPageRepository.findById(pageId)
                .orElseThrow(() -> new IllegalArgumentException("Page not found: " + pageId));

        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new IllegalArgumentException("Role not found: " + roleId));

        User grantedBy = securityValidator.getCurrentUser()
                .orElseThrow(() -> new AccessDeniedException("Not authenticated"));

        // Check if permission already exists
        Optional<PagePermission> existing = permissionRepository.findRolePermission(pageId, roleId, permissionType);
        if (existing.isPresent()) {
            PagePermission permission = existing.get();
            permission.setGranted(true);
            permission.setGrantedBy(grantedBy);
            log.info("Updated permission {} for role {} on page {}",
                    permissionType, role.getName(), page.getTitle());
            return permissionRepository.save(permission);
        }

        // Create new permission
        PagePermission permission = PagePermission.builder()
                .wikiPage(page)
                .role(role)
                .permissionType(permissionType)
                .granted(true)
                .grantedBy(grantedBy)
                .build();

        log.info("Granted permission {} to role {} on page {}",
                permissionType, role.getName(), page.getTitle());

        return permissionRepository.save(permission);
    }

    // ==================== Revoke Permissions ====================

    /**
     * Revoke a user's permission on a wiki page.
     *
     * @param pageId         the wiki page ID
     * @param userId         the user ID to revoke permission from
     * @param permissionType the type of permission to revoke
     */
    @Transactional
    public void revokeUserPermission(Long pageId, Long userId, PermissionType permissionType) {
        validateManagePermissionAccess(pageId);

        Optional<PagePermission> existing = permissionRepository.findUserPermission(pageId, userId, permissionType);
        existing.ifPresent(permission -> {
            permissionRepository.delete(permission);
            log.info("Revoked permission {} from user {} on page {}",
                    permissionType, userId, pageId);
        });
    }

    /**
     * Revoke a role's permission on a wiki page.
     *
     * @param pageId         the wiki page ID
     * @param roleId         the role ID to revoke permission from
     * @param permissionType the type of permission to revoke
     */
    @Transactional
    public void revokeRolePermission(Long pageId, Long roleId, PermissionType permissionType) {
        validateManagePermissionAccess(pageId);

        Optional<PagePermission> existing = permissionRepository.findRolePermission(pageId, roleId, permissionType);
        existing.ifPresent(permission -> {
            permissionRepository.delete(permission);
            log.info("Revoked permission {} from role {} on page {}",
                    permissionType, roleId, pageId);
        });
    }

    /**
     * Revoke all permissions for a page (make it public).
     *
     * @param pageId the wiki page ID
     */
    @Transactional
    public void revokeAllPermissions(Long pageId) {
        validateManagePermissionAccess(pageId);
        permissionRepository.deleteByWikiPageId(pageId);
        log.info("Revoked all permissions for page {}", pageId);
    }

    // ==================== Mark Page as Sensitive ====================

    /**
     * Mark a page as sensitive (restricted).
     * This creates a baseline permission structure where only admins have full access.
     *
     * @param pageId the wiki page ID
     */
    @Transactional
    public void markAsSensitive(Long pageId) {
        // Only admins can mark pages as sensitive
        if (!securityValidator.isAdmin()) {
            throw new AccessDeniedException("Only administrators can mark pages as sensitive");
        }

        WikiPage page = wikiPageRepository.findById(pageId)
                .orElseThrow(() -> new IllegalArgumentException("Page not found: " + pageId));

        User admin = securityValidator.getCurrentUser()
                .orElseThrow(() -> new AccessDeniedException("Not authenticated"));

        // Check if already has permissions
        if (permissionRepository.countByWikiPageId(pageId) > 0) {
            log.info("Page {} is already marked as sensitive", page.getTitle());
            return;
        }

        // Grant admin role full access by default
        Role adminRole = roleRepository.findByName(Role.ADMIN)
                .orElseGet(() -> {
                    Role newRole = Role.builder()
                            .name(Role.ADMIN)
                            .description("Administrator role")
                            .build();
                    return roleRepository.save(newRole);
                });

        PagePermission adminPermission = PagePermission.builder()
                .wikiPage(page)
                .role(adminRole)
                .permissionType(PermissionType.FULL_ACCESS)
                .granted(true)
                .grantedBy(admin)
                .build();

        permissionRepository.save(adminPermission);
        log.info("Marked page {} as sensitive", page.getTitle());
    }

    /**
     * Remove sensitive status from a page (make public).
     *
     * @param pageId the wiki page ID
     */
    @Transactional
    public void markAsPublic(Long pageId) {
        // Only admins can change page sensitivity
        if (!securityValidator.isAdmin()) {
            throw new AccessDeniedException("Only administrators can change page sensitivity");
        }

        revokeAllPermissions(pageId);
        log.info("Marked page {} as public", pageId);
    }

    // ==================== Query Operations ====================

    /**
     * Get all permissions for a page.
     *
     * @param pageId the wiki page ID
     * @return list of permissions
     */
    @Transactional(readOnly = true)
    public List<PagePermission> getPagePermissions(Long pageId) {
        return permissionRepository.findByWikiPageId(pageId);
    }

    /**
     * Get all permissions for a user.
     *
     * @param userId the user ID
     * @return list of permissions
     */
    @Transactional(readOnly = true)
    public List<PagePermission> getUserPermissions(Long userId) {
        return permissionRepository.findByUserId(userId);
    }

    /**
     * Check if a page is sensitive (has explicit permissions).
     *
     * @param pageId the wiki page ID
     * @return true if page is sensitive
     */
    @Transactional(readOnly = true)
    public boolean isSensitive(Long pageId) {
        return permissionRepository.countByWikiPageId(pageId) > 0;
    }

    /**
     * Get all sensitive page IDs.
     *
     * @return set of sensitive page IDs
     */
    @Transactional(readOnly = true)
    public Set<Long> getSensitivePages() {
        return permissionRepository.findPagesWithPermissions();
    }

    /**
     * Get users with access to a page.
     *
     * @param pageId         the wiki page ID
     * @param permissionType the permission type to check
     * @return list of users with the specified permission
     */
    @Transactional(readOnly = true)
    public List<User> getUsersWithPermission(Long pageId, PermissionType permissionType) {
        List<PagePermission> permissions = permissionRepository.findByWikiPageId(pageId);

        return permissions.stream()
                .filter(p -> p.isUserPermission() && p.isGranted())
                .filter(p -> p.getPermissionType() == permissionType ||
                        p.getPermissionType() == PermissionType.FULL_ACCESS)
                .map(PagePermission::getUser)
                .distinct()
                .collect(Collectors.toList());
    }

    // ==================== Helper Methods ====================

    /**
     * Validate that current user can manage permissions for a page.
     */
    private void validateManagePermissionAccess(Long pageId) {
        // Admins can always manage permissions
        if (securityValidator.isAdmin()) {
            return;
        }

        // Check if user has MANAGE_PERMISSIONS on this specific page
        if (!securityValidator.canManagePermissions(pageId)) {
            throw new AccessDeniedException("You do not have permission to manage access for this page");
        }
    }
}