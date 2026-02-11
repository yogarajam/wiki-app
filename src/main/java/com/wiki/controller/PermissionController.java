package com.wiki.controller;

import com.wiki.dto.PermissionDTO;
import com.wiki.model.PagePermission;
import com.wiki.model.PagePermission.PermissionType;
import com.wiki.service.PageSecurityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * REST Controller for managing page permissions.
 */
@RestController
@RequestMapping("/api/permissions")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('ADMIN')")
public class PermissionController {

    private final PageSecurityService pageSecurityService;

    /**
     * Get all permissions for a page.
     */
    @GetMapping("/page/{pageId}")
    public ResponseEntity<List<PermissionDTO>> getPagePermissions(@PathVariable Long pageId) {
        List<PagePermission> permissions = pageSecurityService.getPagePermissions(pageId);

        List<PermissionDTO> dtos = permissions.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());

        return ResponseEntity.ok(dtos);
    }

    /**
     * Grant user permission on a page.
     */
    @PostMapping("/page/{pageId}/user/{userId}")
    public ResponseEntity<PermissionDTO> grantUserPermission(
            @PathVariable Long pageId,
            @PathVariable Long userId,
            @RequestBody GrantPermissionRequest request) {

        PagePermission permission = pageSecurityService.grantUserPermission(
                pageId, userId, request.getPermissionType());

        return ResponseEntity.status(HttpStatus.CREATED).body(convertToDTO(permission));
    }

    /**
     * Grant role permission on a page.
     */
    @PostMapping("/page/{pageId}/role/{roleId}")
    public ResponseEntity<PermissionDTO> grantRolePermission(
            @PathVariable Long pageId,
            @PathVariable Long roleId,
            @RequestBody GrantPermissionRequest request) {

        PagePermission permission = pageSecurityService.grantRolePermission(
                pageId, roleId, request.getPermissionType());

        return ResponseEntity.status(HttpStatus.CREATED).body(convertToDTO(permission));
    }

    /**
     * Revoke user permission from a page.
     */
    @DeleteMapping("/page/{pageId}/user/{userId}")
    public ResponseEntity<Void> revokeUserPermission(
            @PathVariable Long pageId,
            @PathVariable Long userId,
            @RequestParam PermissionType permissionType) {

        pageSecurityService.revokeUserPermission(pageId, userId, permissionType);
        return ResponseEntity.noContent().build();
    }

    /**
     * Revoke role permission from a page.
     */
    @DeleteMapping("/page/{pageId}/role/{roleId}")
    public ResponseEntity<Void> revokeRolePermission(
            @PathVariable Long pageId,
            @PathVariable Long roleId,
            @RequestParam PermissionType permissionType) {

        pageSecurityService.revokeRolePermission(pageId, roleId, permissionType);
        return ResponseEntity.noContent().build();
    }

    /**
     * Revoke all permissions for a page (make public).
     */
    @DeleteMapping("/page/{pageId}")
    public ResponseEntity<Void> revokeAllPermissions(@PathVariable Long pageId) {
        pageSecurityService.revokeAllPermissions(pageId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Mark a page as sensitive (restricted).
     */
    @PostMapping("/page/{pageId}/sensitive")
    public ResponseEntity<String> markAsSensitive(@PathVariable Long pageId) {
        pageSecurityService.markAsSensitive(pageId);
        return ResponseEntity.ok("Page marked as sensitive");
    }

    /**
     * Mark a page as public (unrestricted).
     */
    @PostMapping("/page/{pageId}/public")
    public ResponseEntity<String> markAsPublic(@PathVariable Long pageId) {
        pageSecurityService.markAsPublic(pageId);
        return ResponseEntity.ok("Page marked as public");
    }

    /**
     * Check if a page is sensitive.
     */
    @GetMapping("/page/{pageId}/sensitive")
    public ResponseEntity<Boolean> isSensitive(@PathVariable Long pageId) {
        return ResponseEntity.ok(pageSecurityService.isSensitive(pageId));
    }

    /**
     * Get all sensitive page IDs.
     */
    @GetMapping("/sensitive-pages")
    public ResponseEntity<Set<Long>> getSensitivePages() {
        return ResponseEntity.ok(pageSecurityService.getSensitivePages());
    }

    // ==================== Helper Classes ====================

    private PermissionDTO convertToDTO(PagePermission permission) {
        PermissionDTO dto = new PermissionDTO();
        dto.setId(permission.getId());
        dto.setPageId(permission.getWikiPage().getId());
        dto.setPageTitle(permission.getWikiPage().getTitle());
        dto.setPermissionType(permission.getPermissionType());
        dto.setGranted(permission.isGranted());
        dto.setCreatedAt(permission.getCreatedAt());

        if (permission.isUserPermission()) {
            dto.setUserId(permission.getUser().getId());
            dto.setUsername(permission.getUser().getUsername());
        }

        if (permission.isRolePermission()) {
            dto.setRoleId(permission.getRole().getId());
            dto.setRoleName(permission.getRole().getName());
        }

        if (permission.getGrantedBy() != null) {
            dto.setGrantedById(permission.getGrantedBy().getId());
            dto.setGrantedByUsername(permission.getGrantedBy().getUsername());
        }

        return dto;
    }

    @lombok.Data
    public static class GrantPermissionRequest {
        private PermissionType permissionType;
    }
}