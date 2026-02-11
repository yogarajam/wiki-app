package com.wiki.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wiki.config.SecurityConfig;
import com.wiki.model.PagePermission;
import com.wiki.model.PagePermission.PermissionType;
import com.wiki.model.WikiPage;
import com.wiki.security.WikiUserDetailsService;
import com.wiki.service.PageSecurityService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PermissionController.class)
@Import(SecurityConfig.class)
class PermissionControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PageSecurityService pageSecurityService;

    @MockBean
    private WikiUserDetailsService wikiUserDetailsService;

    // ==================== Admin Access ====================

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void getPagePermissions_shouldSucceedForAdmin() throws Exception {
        when(pageSecurityService.getPagePermissions(1L)).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/permissions/page/1"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void isSensitive_shouldSucceedForAdmin() throws Exception {
        when(pageSecurityService.isSensitive(1L)).thenReturn(true);

        mockMvc.perform(get("/api/permissions/page/1/sensitive"))
                .andExpect(status().isOk())
                .andExpect(content().string("true"));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void getSensitivePages_shouldSucceedForAdmin() throws Exception {
        when(pageSecurityService.getSensitivePages()).thenReturn(Set.of(1L, 3L));

        mockMvc.perform(get("/api/permissions/sensitive-pages"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void markAsSensitive_shouldSucceedForAdmin() throws Exception {
        mockMvc.perform(post("/api/permissions/page/1/sensitive").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string("Page marked as sensitive"));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void markAsPublic_shouldSucceedForAdmin() throws Exception {
        mockMvc.perform(post("/api/permissions/page/1/public").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string("Page marked as public"));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void revokeAllPermissions_shouldSucceedForAdmin() throws Exception {
        mockMvc.perform(delete("/api/permissions/page/1").with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void grantUserPermission_shouldSucceedForAdmin() throws Exception {
        PermissionController.GrantPermissionRequest request = new PermissionController.GrantPermissionRequest();
        request.setPermissionType(PermissionType.VIEW);

        WikiPage mockPage = WikiPage.builder().id(1L).title("Test").slug("test").build();
        PagePermission mockPermission = PagePermission.builder()
                .id(1L)
                .wikiPage(mockPage)
                .permissionType(PermissionType.VIEW)
                .granted(true)
                .build();
        when(pageSecurityService.grantUserPermission(eq(1L), eq(2L), eq(PermissionType.VIEW)))
                .thenReturn(mockPermission);

        mockMvc.perform(post("/api/permissions/page/1/user/2")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void revokeUserPermission_shouldSucceedForAdmin() throws Exception {
        mockMvc.perform(delete("/api/permissions/page/1/user/2")
                        .with(csrf())
                        .param("permissionType", "VIEW"))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void revokeRolePermission_shouldSucceedForAdmin() throws Exception {
        mockMvc.perform(delete("/api/permissions/page/1/role/2")
                        .with(csrf())
                        .param("permissionType", "EDIT"))
                .andExpect(status().isNoContent());
    }

    // ==================== Non-Admin Forbidden ====================

    @Test
    @WithMockUser(username = "editor", roles = {"EDITOR"})
    void getPagePermissions_shouldReturn403ForEditor() throws Exception {
        mockMvc.perform(get("/api/permissions/page/1"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "viewer", roles = {"VIEWER"})
    void getPagePermissions_shouldReturn403ForViewer() throws Exception {
        mockMvc.perform(get("/api/permissions/page/1"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "editor", roles = {"EDITOR"})
    void markAsSensitive_shouldReturn403ForEditor() throws Exception {
        mockMvc.perform(post("/api/permissions/page/1/sensitive").with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "viewer", roles = {"VIEWER"})
    void revokeAllPermissions_shouldReturn403ForViewer() throws Exception {
        mockMvc.perform(delete("/api/permissions/page/1").with(csrf()))
                .andExpect(status().isForbidden());
    }

    // ==================== Anonymous Unauthorized ====================

    @Test
    @WithAnonymousUser
    void getPagePermissions_shouldReturn401ForAnonymous() throws Exception {
        mockMvc.perform(get("/api/permissions/page/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithAnonymousUser
    void markAsSensitive_shouldReturn401ForAnonymous() throws Exception {
        mockMvc.perform(post("/api/permissions/page/1/sensitive").with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithAnonymousUser
    void getSensitivePages_shouldReturn401ForAnonymous() throws Exception {
        mockMvc.perform(get("/api/permissions/sensitive-pages"))
                .andExpect(status().isUnauthorized());
    }
}
