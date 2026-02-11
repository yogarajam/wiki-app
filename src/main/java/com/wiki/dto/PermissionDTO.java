package com.wiki.dto;

import com.wiki.model.PagePermission.PermissionType;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * DTO for page permission data transfer.
 */
@Data
public class PermissionDTO {
    private Long id;
    private Long pageId;
    private String pageTitle;
    private Long userId;
    private String username;
    private Long roleId;
    private String roleName;
    private PermissionType permissionType;
    private boolean granted;
    private Long grantedById;
    private String grantedByUsername;
    private LocalDateTime createdAt;
}