package com.wiki.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wiki.config.SecurityConfig;
import com.wiki.dto.WikiPageDTO;
import com.wiki.model.WikiPage;
import com.wiki.security.SecurityValidator;
import com.wiki.security.WikiUserDetailsService;
import com.wiki.service.WikiPageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(WikiPageController.class)
@Import(SecurityConfig.class)
class WikiPageControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private WikiPageService wikiPageService;

    @MockBean(name = "securityValidator")
    private SecurityValidator securityValidator;

    @MockBean
    private WikiUserDetailsService wikiUserDetailsService;

    private WikiPage createTestPage(Long id, String title, String slug) {
        return WikiPage.builder()
                .id(id)
                .title(title)
                .slug(slug)
                .content("Test content for " + title)
                .published(true)
                .version(1)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    // ==================== Public Endpoints ====================

    @Test
    @WithAnonymousUser
    void getPageTree_shouldBeAccessibleAnonymously() throws Exception {
        WikiPage root = createTestPage(1L, "Root", "root");
        when(wikiPageService.getRootPages()).thenReturn(List.of(root));

        mockMvc.perform(get("/api/pages/tree"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].title", is("Root")))
                .andExpect(jsonPath("$[0].slug", is("root")));
    }

    @Test
    @WithAnonymousUser
    void searchPages_shouldBeAccessibleAnonymously() throws Exception {
        WikiPage page = createTestPage(1L, "Test Page", "test-page");
        when(wikiPageService.search("test")).thenReturn(List.of(page));

        mockMvc.perform(get("/api/pages/search").param("q", "test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].title", is("Test Page")));
    }

    @Test
    @WithAnonymousUser
    void getPageBySlug_shouldBeAccessibleAnonymously() throws Exception {
        WikiPage page = createTestPage(1L, "About", "about");
        when(wikiPageService.findBySlug("about")).thenReturn(Optional.of(page));

        mockMvc.perform(get("/api/pages/slug/about"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title", is("About")))
                .andExpect(jsonPath("$.slug", is("about")));
    }

    @Test
    @WithAnonymousUser
    void getPageBySlug_shouldReturn404WhenNotFound() throws Exception {
        when(wikiPageService.findBySlug("nonexistent")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/pages/slug/nonexistent"))
                .andExpect(status().isNotFound());
    }

    // ==================== Authenticated Endpoints ====================

    @Test
    @WithMockUser(username = "viewer", roles = {"VIEWER"})
    void getPageById_shouldBeAccessibleByAuthenticatedUser() throws Exception {
        WikiPage page = createTestPage(1L, "Test Page", "test-page");
        when(securityValidator.canView(1L)).thenReturn(true);
        when(wikiPageService.findById(1L)).thenReturn(Optional.of(page));

        mockMvc.perform(get("/api/pages/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(1)))
                .andExpect(jsonPath("$.title", is("Test Page")));
    }

    @Test
    @WithAnonymousUser
    void getPageById_shouldReturn401ForAnonymous() throws Exception {
        mockMvc.perform(get("/api/pages/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "viewer", roles = {"VIEWER"})
    void getBacklinks_shouldReturnBacklinks() throws Exception {
        WikiPage linking = createTestPage(2L, "Linking Page", "linking-page");
        when(wikiPageService.getBacklinks(1L)).thenReturn(List.of(linking));

        mockMvc.perform(get("/api/pages/1/backlinks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].title", is("Linking Page")));
    }

    // ==================== Editor Endpoints ====================

    @Test
    @WithMockUser(username = "editor", roles = {"EDITOR"})
    void createPage_shouldSucceedForEditor() throws Exception {
        WikiPageDTO dto = new WikiPageDTO();
        dto.setTitle("New Page");
        dto.setContent("New content");

        WikiPage saved = createTestPage(10L, "New Page", "new-page");
        when(wikiPageService.savePage(any(WikiPage.class), eq(null))).thenReturn(saved);

        mockMvc.perform(post("/api/pages")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", is(10)))
                .andExpect(jsonPath("$.title", is("New Page")))
                .andExpect(jsonPath("$.slug", is("new-page")));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void createPage_shouldSucceedForAdmin() throws Exception {
        WikiPageDTO dto = new WikiPageDTO();
        dto.setTitle("Admin Page");
        dto.setContent("Admin content");

        WikiPage saved = createTestPage(11L, "Admin Page", "admin-page");
        when(wikiPageService.savePage(any(WikiPage.class), eq(null))).thenReturn(saved);

        mockMvc.perform(post("/api/pages")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(username = "viewer", roles = {"VIEWER"})
    void createPage_shouldReturn403ForViewer() throws Exception {
        WikiPageDTO dto = new WikiPageDTO();
        dto.setTitle("Forbidden Page");
        dto.setContent("Content");

        mockMvc.perform(post("/api/pages")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "editor", roles = {"EDITOR"})
    void updatePage_shouldSucceedForEditor() throws Exception {
        WikiPageDTO dto = new WikiPageDTO();
        dto.setTitle("Updated Title");
        dto.setContent("Updated content");

        WikiPage existing = createTestPage(1L, "Old Title", "old-title");
        WikiPage updated = createTestPage(1L, "Updated Title", "old-title");
        updated.setVersion(2);

        when(securityValidator.canEdit(1L)).thenReturn(true);
        when(wikiPageService.findById(1L)).thenReturn(Optional.of(existing));
        when(wikiPageService.savePage(any(WikiPage.class))).thenReturn(updated);

        mockMvc.perform(put("/api/pages/1")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title", is("Updated Title")))
                .andExpect(jsonPath("$.version", is(2)));
    }

    // ==================== Admin-Only Endpoints ====================

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void deletePage_shouldSucceedForAdmin() throws Exception {
        WikiPage page = createTestPage(1L, "To Delete", "to-delete");
        when(securityValidator.canDelete(1L)).thenReturn(true);
        when(wikiPageService.findById(1L)).thenReturn(Optional.of(page));

        mockMvc.perform(delete("/api/pages/1").with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(username = "editor", roles = {"EDITOR"})
    void deletePage_shouldReturn403ForEditor() throws Exception {
        mockMvc.perform(delete("/api/pages/1").with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "viewer", roles = {"VIEWER"})
    void deletePage_shouldReturn403ForViewer() throws Exception {
        mockMvc.perform(delete("/api/pages/1").with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithAnonymousUser
    void deletePage_shouldReturn401ForAnonymous() throws Exception {
        mockMvc.perform(delete("/api/pages/1").with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    // ==================== Response JSON Structure ====================

    @Test
    @WithMockUser(username = "viewer", roles = {"VIEWER"})
    void getPageById_shouldReturnCorrectDTOStructure() throws Exception {
        WikiPage page = WikiPage.builder()
                .id(1L)
                .title("Test Page")
                .slug("test-page")
                .content("Some content")
                .published(true)
                .version(3)
                .createdAt(LocalDateTime.of(2024, 1, 15, 10, 30))
                .updatedAt(LocalDateTime.of(2024, 1, 16, 14, 0))
                .build();

        when(securityValidator.canView(1L)).thenReturn(true);
        when(wikiPageService.findById(1L)).thenReturn(Optional.of(page));

        mockMvc.perform(get("/api/pages/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(1)))
                .andExpect(jsonPath("$.title", is("Test Page")))
                .andExpect(jsonPath("$.slug", is("test-page")))
                .andExpect(jsonPath("$.content", is("Some content")))
                .andExpect(jsonPath("$.version", is(3)))
                .andExpect(jsonPath("$.published", is(true)))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.updatedAt").exists());
    }

    @Test
    @WithAnonymousUser
    void getPageTree_shouldReturnEmptyListWhenNoPages() throws Exception {
        when(wikiPageService.getRootPages()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/pages/tree"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @WithAnonymousUser
    void searchPages_shouldReturnEmptyListForNoResults() throws Exception {
        when(wikiPageService.search("nonexistent")).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/pages/search").param("q", "nonexistent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }
}
