package com.wiki.security;

import com.wiki.model.Role;
import com.wiki.model.User;
import com.wiki.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("WikiUserDetailsService Tests")
class WikiUserDetailsServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private WikiUserDetailsService userDetailsService;

    private User activeUser;
    private User disabledUser;
    private User lockedUser;

    @BeforeEach
    void setUp() {
        Role adminRole = Role.builder().id(1L).name(Role.ADMIN).build();
        Role editorRole = Role.builder().id(2L).name(Role.EDITOR).build();

        activeUser = User.builder()
                .id(1L)
                .username("admin")
                .password("encoded-password")
                .email("admin@example.com")
                .enabled(true)
                .accountNonLocked(true)
                .roles(Set.of(adminRole, editorRole))
                .build();

        disabledUser = User.builder()
                .id(2L)
                .username("disabled")
                .password("encoded-password")
                .email("disabled@example.com")
                .enabled(false)
                .accountNonLocked(true)
                .roles(Set.of(editorRole))
                .build();

        lockedUser = User.builder()
                .id(3L)
                .username("locked")
                .password("encoded-password")
                .email("locked@example.com")
                .enabled(true)
                .accountNonLocked(false)
                .roles(Set.of(editorRole))
                .build();
    }

    @Nested
    @DisplayName("Successful Authentication Tests")
    class SuccessfulAuthTests {

        @Test
        @DisplayName("Should load active user with correct authorities")
        void shouldLoadActiveUser() {
            when(userRepository.findByUsernameWithRoles("admin"))
                    .thenReturn(Optional.of(activeUser));

            UserDetails userDetails = userDetailsService.loadUserByUsername("admin");

            assertThat(userDetails.getUsername()).isEqualTo("admin");
            assertThat(userDetails.getPassword()).isEqualTo("encoded-password");
            assertThat(userDetails.isEnabled()).isTrue();
            assertThat(userDetails.isAccountNonLocked()).isTrue();
            assertThat(userDetails.getAuthorities()).hasSize(2);
            assertThat(userDetails.getAuthorities())
                    .extracting("authority")
                    .containsExactlyInAnyOrder(Role.ADMIN, Role.EDITOR);
        }
    }

    @Nested
    @DisplayName("Failed Authentication Tests")
    class FailedAuthTests {

        @Test
        @DisplayName("Should throw exception for non-existent user")
        void shouldThrowForNonExistentUser() {
            when(userRepository.findByUsernameWithRoles("unknown"))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> userDetailsService.loadUserByUsername("unknown"))
                    .isInstanceOf(UsernameNotFoundException.class)
                    .hasMessageContaining("unknown");
        }

        @Test
        @DisplayName("Should throw exception for disabled user")
        void shouldThrowForDisabledUser() {
            when(userRepository.findByUsernameWithRoles("disabled"))
                    .thenReturn(Optional.of(disabledUser));

            assertThatThrownBy(() -> userDetailsService.loadUserByUsername("disabled"))
                    .isInstanceOf(UsernameNotFoundException.class)
                    .hasMessageContaining("disabled");
        }

        @Test
        @DisplayName("Should throw exception for locked user")
        void shouldThrowForLockedUser() {
            when(userRepository.findByUsernameWithRoles("locked"))
                    .thenReturn(Optional.of(lockedUser));

            assertThatThrownBy(() -> userDetailsService.loadUserByUsername("locked"))
                    .isInstanceOf(UsernameNotFoundException.class)
                    .hasMessageContaining("locked");
        }
    }
}