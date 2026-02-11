package com.wiki.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("User Model Tests")
class UserTest {

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .id(1L)
                .username("testuser")
                .email("test@example.com")
                .password("encoded-password")
                .build();
    }

    @Nested
    @DisplayName("Role Management Tests")
    class RoleManagementTests {

        @Test
        @DisplayName("Should add role to user")
        void shouldAddRole() {
            Role role = Role.builder().id(1L).name(Role.EDITOR).build();

            user.addRole(role);

            assertThat(user.getRoles()).contains(role);
        }

        @Test
        @DisplayName("Should remove role from user")
        void shouldRemoveRole() {
            Role role = Role.builder().id(1L).name(Role.EDITOR).build();
            user.addRole(role);

            user.removeRole(role);

            assertThat(user.getRoles()).doesNotContain(role);
        }

        @Test
        @DisplayName("hasRole should return true when user has the role")
        void hasRoleShouldReturnTrue() {
            Role role = Role.builder().id(1L).name(Role.ADMIN).build();
            user.addRole(role);

            assertThat(user.hasRole(Role.ADMIN)).isTrue();
        }

        @Test
        @DisplayName("hasRole should return false when user lacks the role")
        void hasRoleShouldReturnFalse() {
            assertThat(user.hasRole(Role.ADMIN)).isFalse();
        }
    }

    @Nested
    @DisplayName("Admin Check Tests")
    class AdminCheckTests {

        @Test
        @DisplayName("isAdmin should return true for admin user")
        void isAdminShouldReturnTrue() {
            Role adminRole = Role.builder().id(1L).name(Role.ADMIN).build();
            user.addRole(adminRole);

            assertThat(user.isAdmin()).isTrue();
        }

        @Test
        @DisplayName("isAdmin should return false for non-admin user")
        void isAdminShouldReturnFalse() {
            Role editorRole = Role.builder().id(2L).name(Role.EDITOR).build();
            user.addRole(editorRole);

            assertThat(user.isAdmin()).isFalse();
        }

        @Test
        @DisplayName("isAdmin should return false for user with no roles")
        void isAdminShouldReturnFalseForNoRoles() {
            assertThat(user.isAdmin()).isFalse();
        }
    }

    @Nested
    @DisplayName("Default Values Tests")
    class DefaultValuesTests {

        @Test
        @DisplayName("Should have enabled = true by default")
        void shouldBeEnabledByDefault() {
            assertThat(user.isEnabled()).isTrue();
        }

        @Test
        @DisplayName("Should have accountNonLocked = true by default")
        void shouldBeNonLockedByDefault() {
            assertThat(user.isAccountNonLocked()).isTrue();
        }

        @Test
        @DisplayName("Should have empty roles by default")
        void shouldHaveEmptyRolesByDefault() {
            assertThat(user.getRoles()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Equals and HashCode Tests")
    class EqualsAndHashCodeTests {

        @Test
        @DisplayName("Users with same ID should be equal")
        void usersWithSameIdShouldBeEqual() {
            User user2 = User.builder().id(1L).username("other").build();
            assertThat(user).isEqualTo(user2);
        }

        @Test
        @DisplayName("Users with different IDs should not be equal")
        void usersWithDifferentIdsShouldNotBeEqual() {
            User user2 = User.builder().id(2L).username("testuser").build();
            assertThat(user).isNotEqualTo(user2);
        }

        @Test
        @DisplayName("User should not equal null")
        void userShouldNotEqualNull() {
            assertThat(user).isNotEqualTo(null);
        }
    }
}