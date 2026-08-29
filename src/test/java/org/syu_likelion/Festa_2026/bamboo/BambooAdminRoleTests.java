package org.syu_likelion.Festa_2026.bamboo;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import org.junit.jupiter.api.Test;
import org.syu_likelion.Festa_2026.error.ApiException;
import org.syu_likelion.Festa_2026.user.FestivalRole;

class BambooAdminRoleTests {

    @Test
    void staffLevelAcceptsStaffAdminAndSuperAdmin() {
        assertAllowed(FestivalRole.STAFF, FestivalRole.STAFF);
        assertAllowed(FestivalRole.STAFF, FestivalRole.ADMIN);
        assertAllowed(FestivalRole.STAFF, FestivalRole.SUPER_ADMIN);
    }

    @Test
    void adminLevelRejectsStaff() {
        assertAllowed(FestivalRole.ADMIN, FestivalRole.ADMIN);
        assertAllowed(FestivalRole.ADMIN, FestivalRole.SUPER_ADMIN);
        assertRejected(FestivalRole.ADMIN, FestivalRole.STAFF);
    }

    @Test
    void superAdminLevelRejectsAdmin() {
        assertAllowed(FestivalRole.SUPER_ADMIN, FestivalRole.SUPER_ADMIN);
        assertRejected(FestivalRole.SUPER_ADMIN, FestivalRole.ADMIN);
        assertRejected(FestivalRole.SUPER_ADMIN, FestivalRole.STAFF);
    }

    @Test
    void plainUsersAndBoothManagersAreNeverAllowed() {
        assertRejected(FestivalRole.STAFF, FestivalRole.USER);
        assertRejected(FestivalRole.STAFF, FestivalRole.BOOTH_MANAGER);
    }

    @Test
    void missingRolesAreRejected() {
        assertThatThrownBy(() -> BambooAdminService.requireRole(null, FestivalRole.STAFF))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> BambooAdminService.requireRole(Set.of(), FestivalRole.STAFF))
                .isInstanceOf(ApiException.class);
    }

    private void assertAllowed(FestivalRole required, FestivalRole held) {
        assertThatCode(() -> BambooAdminService.requireRole(Set.of(held), required))
                .as("%s 는 %s 를 통과해야 함", held, required)
                .doesNotThrowAnyException();
    }

    private void assertRejected(FestivalRole required, FestivalRole held) {
        assertThatThrownBy(() -> BambooAdminService.requireRole(Set.of(held), required))
                .as("%s 는 %s 에서 막혀야 함", held, required)
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).code())
                .isEqualTo("BAMBOO_MANAGE_FORBIDDEN");
    }
}
