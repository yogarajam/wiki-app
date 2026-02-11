package com.wiki.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * PagePermission entity for fine-grained access control on wiki pages.
 * Allows setting specific permissions for users or roles on sensitive pages.
 */
@Entity
@Table(name = "page_permissions", indexes = {
        @Index(name = "idx_permission_page", columnList = "wiki_page_id"),
        @Index(name = "idx_permission_user", columnList = "user_id"),
        @Index(name = "idx_permission_role", columnList = "role_id")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_page_user_permission",
                columnNames = {"wiki_page_id", "user_id", "permission_type"}),
        @UniqueConstraint(name = "uk_page_role_permission",
                columnNames = {"wiki_page_id", "role_id", "permission_type"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PagePermission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wiki_page_id", nullable = false)
    private WikiPage wikiPage;

    /**
     * The user this permission applies to (null if role-based).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    /**
     * The role this permission applies to (null if user-based).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id")
    private Role role;

    /**
     * Type of permission granted.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "permission_type", nullable = false)
    private PermissionType permissionType;

    /**
     * Whether this is an explicit grant or deny.
     */
    @Column(name = "granted", nullable = false)
    @Builder.Default
    private boolean granted = true;

    /**
     * Who granted this permission.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "granted_by")
    private User grantedBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Permission types for wiki pages.
     */
    public enum PermissionType {
        /**
         * Can view the page content.
         */
        VIEW,

        /**
         * Can edit the page content.
         */
        EDIT,

        /**
         * Can delete the page.
         */
        DELETE,

        /**
         * Can manage permissions for the page.
         */
        MANAGE_PERMISSIONS,

        /**
         * Full access (includes all permissions).
         */
        FULL_ACCESS
    }

    /**
     * Check if this permission is for a specific user.
     */
    public boolean isUserPermission() {
        return user != null;
    }

    /**
     * Check if this permission is for a role.
     */
    public boolean isRolePermission() {
        return role != null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PagePermission that = (PagePermission) o;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}