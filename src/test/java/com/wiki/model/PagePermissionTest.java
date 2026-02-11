package com.wiki.model;

import com.wiki.model.PagePermission.PermissionType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("PagePermission Model Tests")
class PagePermissionTest {

    @Nested
    @DisplayName("Permission Type Tests")
    class PermissionTypeTests {

        @Test
        @DisplayName("isUserPermission should return true when user is set")
        void isUserPermissionShouldReturnTrue() {
            User user = User.builder().id(1L).username("test").build();
            PagePermission permission = PagePermission.builder()
                    .id(1L)
                    .user(user)
                    .permissionType(PermissionType.EDIT)
                    .build();

            assertThat(permission.isUserPermission()).isTrue();
            assertThat(permission.isRolePermission()).isFalse();
        }

        @Test
        @DisplayName("isRolePermission should return true when role is set")
        void isRolePermissionShouldReturnTrue() {
            Role role = Role.builder().id(1L).name(Role.EDITOR).build();
            PagePermission permission = PagePermission.builder()
                    .id(1L)
                    .role(role)
                    .permissionType(PermissionType.EDIT)
                    .build();

            assertThat(permission.isRolePermission()).isTrue();
            assertThat(permission.isUserPermission()).isFalse();
        }
    }

    @Nested
    @DisplayName("Default Values Tests")
    class DefaultValuesTests {

        @Test
        @DisplayName("Should have granted = true by default")
        void shouldBeGrantedByDefault() {
            PagePermission permission = PagePermission.builder()
                    .permissionType(PermissionType.VIEW)
                    .build();

            assertThat(permission.isGranted()).isTrue();
        }
    }

    @Nested
    @DisplayName("PermissionType Enum Tests")
    class PermissionTypeEnumTests {

        @Test
        @DisplayName("Should have all expected permission types")
        void shouldHaveAllPermissionTypes() {
            assertThat(PermissionType.values()).containsExactlyInAnyOrder(
                    PermissionType.VIEW,
                    PermissionType.EDIT,
                    PermissionType.DELETE,
                    PermissionType.MANAGE_PERMISSIONS,
                    PermissionType.FULL_ACCESS
            );
        }
    }

    @Nested
    @DisplayName("Equals and HashCode Tests")
    class EqualsAndHashCodeTests {

        @Test
        @DisplayName("Permissions with same ID should be equal")
        void permissionsWithSameIdShouldBeEqual() {
            PagePermission p1 = PagePermission.builder().id(1L).permissionType(PermissionType.VIEW).build();
            PagePermission p2 = PagePermission.builder().id(1L).permissionType(PermissionType.EDIT).build();

            assertThat(p1).isEqualTo(p2);
        }

        @Test
        @DisplayName("Permissions with different IDs should not be equal")
        void permissionsWithDifferentIdsShouldNotBeEqual() {
            PagePermission p1 = PagePermission.builder().id(1L).permissionType(PermissionType.VIEW).build();
            PagePermission p2 = PagePermission.builder().id(2L).permissionType(PermissionType.VIEW).build();

            assertThat(p1).isNotEqualTo(p2);
        }
    }
}