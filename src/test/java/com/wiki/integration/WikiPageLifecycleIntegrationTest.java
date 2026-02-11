package com.wiki.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wiki.dto.WikiPageDTO;
import com.wiki.model.Role;
import com.wiki.model.User;
import com.wiki.model.WikiPage;
import com.wiki.repository.RoleRepository;
import com.wiki.repository.UserRepository;
import com.wiki.repository.WikiPageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.s3.S3Client;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class WikiPageLifecycleIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private WikiPageRepository wikiPageRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockBean
    private S3Client s3Client;

    private User adminUser;
    private User editorUser;
    private User viewerUser;

    @BeforeEach
    void setUp() {
        Role adminRole = roleRepository.save(Role.builder()
                .name("ROLE_ADMIN")
                .description("Administrator")
                .build());

        Role editorRole = roleRepository.save(Role.builder()
                .name("ROLE_EDITOR")
                .description("Editor")
                .build());

        Role viewerRole = roleRepository.save(Role.builder()
                .name("ROLE_VIEWER")
                .description("Viewer")
                .build());

        adminUser = User.builder()
                .username("admin")
                .email("admin@test.com")
                .password(passwordEncoder.encode("admin123"))
                .displayName("Admin")
                .enabled(true)
                .accountNonLocked(true)
                .build();
        adminUser.addRole(adminRole);
        adminUser = userRepository.save(adminUser);

        editorUser = User.builder()
                .username("editor")
                .email("editor@test.com")
                .password(passwordEncoder.encode("editor123"))
                .displayName("Editor")
                .enabled(true)
                .accountNonLocked(true)
                .build();
        editorUser.addRole(editorRole);
        editorUser = userRepository.save(editorUser);

        viewerUser = User.builder()
                .username("viewer")
                .email("viewer@test.com")
                .password(passwordEncoder.encode("viewer123"))
                .displayName("Viewer")
                .enabled(true)
                .accountNonLocked(true)
                .build();
        viewerUser.addRole(viewerRole);
        viewerUser = userRepository.save(viewerUser);
    }

    // ==================== Full Page Lifecycle ====================

    @Test
    void shouldCreateReadUpdateDeletePage() throws Exception {
        // CREATE
        WikiPageDTO createDto = new WikiPageDTO();
        createDto.setTitle("Lifecycle Page");
        createDto.setContent("Initial content");

        MvcResult createResult = mockMvc.perform(post("/api/pages")
                        .with(httpBasic("editor", "editor123"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createDto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title", is("Lifecycle Page")))
                .andExpect(jsonPath("$.slug", is("lifecycle-page")))
                .andExpect(jsonPath("$.version", is(1)))
                .andReturn();

        WikiPageDTO created = objectMapper.readValue(
                createResult.getResponse().getContentAsString(), WikiPageDTO.class);
        Long pageId = created.getId();

        // READ by ID (viewer can read since page has no explicit permissions)
        mockMvc.perform(get("/api/pages/" + pageId)
                        .with(httpBasic("viewer", "viewer123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title", is("Lifecycle Page")));

        // READ by slug (public endpoint)
        mockMvc.perform(get("/api/pages/slug/lifecycle-page"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title", is("Lifecycle Page")));

        // UPDATE
        WikiPageDTO updateDto = new WikiPageDTO();
        updateDto.setTitle("Updated Lifecycle Page");
        updateDto.setContent("Updated content");

        mockMvc.perform(put("/api/pages/" + pageId)
                        .with(httpBasic("editor", "editor123"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title", is("Updated Lifecycle Page")))
                .andExpect(jsonPath("$.version", is(2)));

        // DELETE (admin only)
        mockMvc.perform(delete("/api/pages/" + pageId)
                        .with(httpBasic("admin", "admin123"))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        // Verify deleted via slug endpoint (public, returns 404)
        mockMvc.perform(get("/api/pages/slug/lifecycle-page"))
                .andExpect(status().isNotFound());
    }

    // ==================== Page Hierarchy ====================

    @Test
    void shouldCreatePageHierarchyAndVerifyViaRepository() throws Exception {
        // Create parent
        WikiPageDTO parentDto = new WikiPageDTO();
        parentDto.setTitle("Parent Page");
        parentDto.setContent("Parent content");

        MvcResult parentResult = mockMvc.perform(post("/api/pages")
                        .with(httpBasic("editor", "editor123"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(parentDto)))
                .andExpect(status().isCreated())
                .andReturn();

        WikiPageDTO parent = objectMapper.readValue(
                parentResult.getResponse().getContentAsString(), WikiPageDTO.class);

        // Create child under parent
        WikiPageDTO childDto = new WikiPageDTO();
        childDto.setTitle("Child Page");
        childDto.setContent("Child content");
        childDto.setParentId(parent.getId());

        mockMvc.perform(post("/api/pages")
                        .with(httpBasic("editor", "editor123"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(childDto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.parentId", is(parent.getId().intValue())));

        // Verify tree via repository
        List<WikiPage> roots = wikiPageRepository.findAllRootPages();
        assertThat(roots).hasSize(1);
        assertThat(roots.get(0).getTitle()).isEqualTo("Parent Page");

        List<WikiPage> children = wikiPageRepository.findChildrenByParentId(parent.getId());
        assertThat(children).hasSize(1);
        assertThat(children.get(0).getTitle()).isEqualTo("Child Page");
    }

    // ==================== Backlink Resolution ====================

    @Test
    void shouldResolveBacklinks() throws Exception {
        // Create target page first
        WikiPageDTO targetDto = new WikiPageDTO();
        targetDto.setTitle("Target Page");
        targetDto.setContent("Target content");

        MvcResult targetResult = mockMvc.perform(post("/api/pages")
                        .with(httpBasic("editor", "editor123"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(targetDto)))
                .andExpect(status().isCreated())
                .andReturn();

        WikiPageDTO target = objectMapper.readValue(
                targetResult.getResponse().getContentAsString(), WikiPageDTO.class);

        // Create source page that links to target
        WikiPageDTO sourceDto = new WikiPageDTO();
        sourceDto.setTitle("Source Page");
        sourceDto.setContent("This links to [[Target Page]] here.");

        mockMvc.perform(post("/api/pages")
                        .with(httpBasic("editor", "editor123"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sourceDto)))
                .andExpect(status().isCreated());

        // Verify backlinks via repository
        List<WikiPage> backlinks = wikiPageRepository.findPagesLinkingTo(target.getId());
        assertThat(backlinks).hasSize(1);
        assertThat(backlinks.get(0).getTitle()).isEqualTo("Source Page");
    }

    // ==================== Search Integration ====================

    @Test
    void shouldSearchPages() throws Exception {
        // Create pages with searchable content
        WikiPageDTO page1 = new WikiPageDTO();
        page1.setTitle("Java Programming");
        page1.setContent("Java is a popular programming language");

        mockMvc.perform(post("/api/pages")
                        .with(httpBasic("editor", "editor123"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(page1)))
                .andExpect(status().isCreated());

        WikiPageDTO page2 = new WikiPageDTO();
        page2.setTitle("Python Guide");
        page2.setContent("Python is great for data science");

        mockMvc.perform(post("/api/pages")
                        .with(httpBasic("editor", "editor123"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(page2)))
                .andExpect(status().isCreated());

        // Search for Java (public endpoint)
        mockMvc.perform(get("/api/pages/search").param("q", "Java"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].title", is("Java Programming")));

        // Search for "programming" - should find Java page (in content)
        mockMvc.perform(get("/api/pages/search").param("q", "programming"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));

        // Search for something that doesn't exist
        mockMvc.perform(get("/api/pages/search").param("q", "nonexistent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    // ==================== Security End-to-End ====================

    @Test
    @WithAnonymousUser
    void anonymousUser_canAccessPublicEndpoints() throws Exception {
        mockMvc.perform(get("/api/pages/tree"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/pages/search").param("q", "test"))
                .andExpect(status().isOk());
    }

    @Test
    @WithAnonymousUser
    void anonymousUser_cannotAccessProtectedEndpoints() throws Exception {
        mockMvc.perform(get("/api/pages/1"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/pages")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void viewerUser_canReadButCannotWrite() throws Exception {
        // Create a page as editor first
        WikiPageDTO dto = new WikiPageDTO();
        dto.setTitle("Viewer Test Page");
        dto.setContent("Content");

        MvcResult result = mockMvc.perform(post("/api/pages")
                        .with(httpBasic("editor", "editor123"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andReturn();

        WikiPageDTO created = objectMapper.readValue(
                result.getResponse().getContentAsString(), WikiPageDTO.class);

        // Viewer can read
        mockMvc.perform(get("/api/pages/" + created.getId())
                        .with(httpBasic("viewer", "viewer123")))
                .andExpect(status().isOk());

        // Viewer cannot create
        mockMvc.perform(post("/api/pages")
                        .with(httpBasic("viewer", "viewer123"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isForbidden());

        // Viewer cannot delete
        mockMvc.perform(delete("/api/pages/" + created.getId())
                        .with(httpBasic("viewer", "viewer123"))
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void editorUser_canCreateAndUpdateButCannotDelete() throws Exception {
        // Editor can create
        WikiPageDTO dto = new WikiPageDTO();
        dto.setTitle("Editor Test Page");
        dto.setContent("Content");

        MvcResult result = mockMvc.perform(post("/api/pages")
                        .with(httpBasic("editor", "editor123"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andReturn();

        WikiPageDTO created = objectMapper.readValue(
                result.getResponse().getContentAsString(), WikiPageDTO.class);

        // Editor can update
        WikiPageDTO updateDto = new WikiPageDTO();
        updateDto.setTitle("Updated Editor Test Page");
        updateDto.setContent("Updated content");

        mockMvc.perform(put("/api/pages/" + created.getId())
                        .with(httpBasic("editor", "editor123"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateDto)))
                .andExpect(status().isOk());

        // Editor cannot delete
        mockMvc.perform(delete("/api/pages/" + created.getId())
                        .with(httpBasic("editor", "editor123"))
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminUser_canDoEverything() throws Exception {
        // Admin can create
        WikiPageDTO dto = new WikiPageDTO();
        dto.setTitle("Admin Test Page");
        dto.setContent("Content");

        MvcResult result = mockMvc.perform(post("/api/pages")
                        .with(httpBasic("admin", "admin123"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andReturn();

        WikiPageDTO created = objectMapper.readValue(
                result.getResponse().getContentAsString(), WikiPageDTO.class);

        // Admin can update
        WikiPageDTO updateDto = new WikiPageDTO();
        updateDto.setTitle("Updated Admin Test Page");
        updateDto.setContent("Updated content");

        mockMvc.perform(put("/api/pages/" + created.getId())
                        .with(httpBasic("admin", "admin123"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateDto)))
                .andExpect(status().isOk());

        // Admin can delete
        mockMvc.perform(delete("/api/pages/" + created.getId())
                        .with(httpBasic("admin", "admin123"))
                        .with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    void adminUser_canAccessPermissionEndpoints() throws Exception {
        // Create a page first
        WikiPageDTO dto = new WikiPageDTO();
        dto.setTitle("Permission Test Page");
        dto.setContent("Content");

        MvcResult result = mockMvc.perform(post("/api/pages")
                        .with(httpBasic("admin", "admin123"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andReturn();

        WikiPageDTO created = objectMapper.readValue(
                result.getResponse().getContentAsString(), WikiPageDTO.class);

        // Admin can view permissions
        mockMvc.perform(get("/api/permissions/page/" + created.getId())
                        .with(httpBasic("admin", "admin123")))
                .andExpect(status().isOk());

        // Admin can check sensitivity
        mockMvc.perform(get("/api/permissions/page/" + created.getId() + "/sensitive")
                        .with(httpBasic("admin", "admin123")))
                .andExpect(status().isOk())
                .andExpect(content().string("false"));

        // Admin can view sensitive pages list
        mockMvc.perform(get("/api/permissions/sensitive-pages")
                        .with(httpBasic("admin", "admin123")))
                .andExpect(status().isOk());
    }

    // ==================== Slug Generation ====================

    @Test
    void shouldGenerateCorrectSlug() throws Exception {
        WikiPageDTO dto = new WikiPageDTO();
        dto.setTitle("Unique Title A");
        dto.setContent("Content A");

        MvcResult result = mockMvc.perform(post("/api/pages")
                        .with(httpBasic("editor", "editor123"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andReturn();

        WikiPageDTO created = objectMapper.readValue(
                result.getResponse().getContentAsString(), WikiPageDTO.class);
        assertThat(created.getSlug()).isEqualTo("unique-title-a");

        // Verify we can access it by slug
        mockMvc.perform(get("/api/pages/slug/unique-title-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title", is("Unique Title A")));
    }
}
