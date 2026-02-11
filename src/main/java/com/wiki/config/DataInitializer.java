package com.wiki.config;

import com.wiki.model.Role;
import com.wiki.model.User;
import com.wiki.repository.RoleRepository;
import com.wiki.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Initializes default roles and users for development/testing.
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class DataInitializer {

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Bean
    @Profile("!test")
    public CommandLineRunner initializeData() {
        return args -> {
            // Create default roles
            Role adminRole = createRoleIfNotExists(Role.ADMIN, "Administrator with full access");
            Role editorRole = createRoleIfNotExists(Role.EDITOR, "Can view and edit pages");
            Role viewerRole = createRoleIfNotExists(Role.VIEWER, "Can view pages only");

            // Create default admin user if not exists
            if (!userRepository.existsByUsername("admin")) {
                User admin = User.builder()
                        .username("admin")
                        .email("admin@wiki.local")
                        .password(passwordEncoder.encode("admin123"))
                        .displayName("Administrator")
                        .enabled(true)
                        .accountNonLocked(true)
                        .build();
                admin.addRole(adminRole);
                userRepository.save(admin);
                log.info("Created default admin user: admin/admin123");
            }

            // Create default editor user if not exists
            if (!userRepository.existsByUsername("editor")) {
                User editor = User.builder()
                        .username("editor")
                        .email("editor@wiki.local")
                        .password(passwordEncoder.encode("editor123"))
                        .displayName("Editor User")
                        .enabled(true)
                        .accountNonLocked(true)
                        .build();
                editor.addRole(editorRole);
                userRepository.save(editor);
                log.info("Created default editor user: editor/editor123");
            }

            // Create default viewer user if not exists
            if (!userRepository.existsByUsername("viewer")) {
                User viewer = User.builder()
                        .username("viewer")
                        .email("viewer@wiki.local")
                        .password(passwordEncoder.encode("viewer123"))
                        .displayName("Viewer User")
                        .enabled(true)
                        .accountNonLocked(true)
                        .build();
                viewer.addRole(viewerRole);
                userRepository.save(viewer);
                log.info("Created default viewer user: viewer/viewer123");
            }
        };
    }

    private Role createRoleIfNotExists(String name, String description) {
        return roleRepository.findByName(name)
                .orElseGet(() -> {
                    Role role = Role.builder()
                            .name(name)
                            .description(description)
                            .build();
                    role = roleRepository.save(role);
                    log.info("Created role: {}", name);
                    return role;
                });
    }
}