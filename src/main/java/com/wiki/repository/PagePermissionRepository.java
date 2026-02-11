package com.wiki.repository;

import com.wiki.model.PagePermission;
import com.wiki.model.PagePermission.PermissionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Repository for PagePermission entity.
 */
@Repository
public interface PagePermissionRepository extends JpaRepository<PagePermission, Long> {

    /**
     * Find all permissions for a page.
     */
    List<PagePermission> findByWikiPageId(Long wikiPageId);

    /**
     * Find all permissions for a user.
     */
    List<PagePermission> findByUserId(Long userId);

    /**
     * Find all permissions for a role.
     */
    List<PagePermission> findByRoleId(Long roleId);

    /**
     * Find user permission for a specific page and permission type.
     */
    @Query("""
            SELECT pp FROM PagePermission pp
            WHERE pp.wikiPage.id = :pageId
            AND pp.user.id = :userId
            AND pp.permissionType = :permissionType
            """)
    Optional<PagePermission> findUserPermission(
            @Param("pageId") Long pageId,
            @Param("userId") Long userId,
            @Param("permissionType") PermissionType permissionType);

    /**
     * Find role permission for a specific page and permission type.
     */
    @Query("""
            SELECT pp FROM PagePermission pp
            WHERE pp.wikiPage.id = :pageId
            AND pp.role.id = :roleId
            AND pp.permissionType = :permissionType
            """)
    Optional<PagePermission> findRolePermission(
            @Param("pageId") Long pageId,
            @Param("roleId") Long roleId,
            @Param("permissionType") PermissionType permissionType);

    /**
     * Find all granted permissions for a user on a specific page.
     */
    @Query("""
            SELECT pp FROM PagePermission pp
            WHERE pp.wikiPage.id = :pageId
            AND pp.user.id = :userId
            AND pp.granted = true
            """)
    List<PagePermission> findGrantedUserPermissions(
            @Param("pageId") Long pageId,
            @Param("userId") Long userId);

    /**
     * Find all granted permissions for roles on a specific page.
     */
    @Query("""
            SELECT pp FROM PagePermission pp
            WHERE pp.wikiPage.id = :pageId
            AND pp.role.id IN :roleIds
            AND pp.granted = true
            """)
    List<PagePermission> findGrantedRolePermissions(
            @Param("pageId") Long pageId,
            @Param("roleIds") Set<Long> roleIds);

    /**
     * Check if user has specific permission on page.
     */
    @Query("""
            SELECT COUNT(pp) > 0 FROM PagePermission pp
            WHERE pp.wikiPage.id = :pageId
            AND pp.user.id = :userId
            AND pp.permissionType IN :permissionTypes
            AND pp.granted = true
            """)
    boolean hasUserPermission(
            @Param("pageId") Long pageId,
            @Param("userId") Long userId,
            @Param("permissionTypes") Set<PermissionType> permissionTypes);

    /**
     * Check if any of the user's roles has specific permission on page.
     */
    @Query("""
            SELECT COUNT(pp) > 0 FROM PagePermission pp
            WHERE pp.wikiPage.id = :pageId
            AND pp.role.id IN :roleIds
            AND pp.permissionType IN :permissionTypes
            AND pp.granted = true
            """)
    boolean hasRolePermission(
            @Param("pageId") Long pageId,
            @Param("roleIds") Set<Long> roleIds,
            @Param("permissionTypes") Set<PermissionType> permissionTypes);

    /**
     * Delete all permissions for a page.
     */
    void deleteByWikiPageId(Long wikiPageId);

    /**
     * Delete all permissions for a user.
     */
    void deleteByUserId(Long userId);

    /**
     * Find pages that have any explicit permissions set (sensitive pages).
     */
    @Query("SELECT DISTINCT pp.wikiPage.id FROM PagePermission pp")
    Set<Long> findPagesWithPermissions();

    /**
     * Count permissions for a page.
     */
    long countByWikiPageId(Long wikiPageId);
}