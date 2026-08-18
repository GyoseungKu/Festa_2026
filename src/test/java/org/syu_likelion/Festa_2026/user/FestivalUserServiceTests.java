package org.syu_likelion.Festa_2026.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.syu_likelion.Festa_2026.error.ApiException;

class FestivalUserServiceTests {
    private static final UUID ACTOR = UUID.fromString("123e4567-e89b-12d3-a456-426614174301");
    private static final UUID TARGET = UUID.fromString("123e4567-e89b-12d3-a456-426614174302");
    private FestivalUserRepository repository;
    private FestivalUserService service;

    @BeforeEach void setUp() {
        repository = mock(FestivalUserRepository.class);
        service = new FestivalUserService(repository);
    }

    @Test void adminCanChangeUserToStaffWithoutRemovingBoothManagerRole() {
        FestivalUser target = new FestivalUser(TARGET);
        target.addRole(FestivalRole.BOOTH_MANAGER);
        when(repository.findByUserUuid(TARGET)).thenReturn(Optional.of(target));
        when(repository.findByUserUuidForUpdate(TARGET)).thenReturn(Optional.of(target));

        assertThat(service.updateManagementRole(ACTOR, FestivalRole.ADMIN, TARGET, FestivalRole.STAFF))
                .containsExactlyInAnyOrder(FestivalRole.STAFF, FestivalRole.BOOTH_MANAGER);
        assertThat(target.getManagementRole()).isEqualTo(FestivalRole.STAFF);
        assertThat(target.isBoothManager()).isTrue();
    }

    @Test void adminCannotPromoteUserToAdminOrModifyAnotherAdmin() {
        FestivalUser user = new FestivalUser(TARGET);
        when(repository.findByUserUuid(TARGET)).thenReturn(Optional.of(user));
        when(repository.findByUserUuidForUpdate(TARGET)).thenReturn(Optional.of(user));
        assertThatThrownBy(() -> service.updateManagementRole(ACTOR, FestivalRole.ADMIN, TARGET, FestivalRole.ADMIN))
                .isInstanceOfSatisfying(ApiException.class, error ->
                        assertThat(error.code()).isEqualTo("USER_ROLE_ESCALATION_FORBIDDEN"));

        user.changeManagementRole(FestivalRole.ADMIN);
        assertThatThrownBy(() -> service.updateManagementRole(ACTOR, FestivalRole.ADMIN, TARGET, FestivalRole.STAFF))
                .isInstanceOfSatisfying(ApiException.class, error ->
                        assertThat(error.code()).isEqualTo("USER_ROLE_ESCALATION_FORBIDDEN"));
    }

    @Test void adminCannotChangeOwnRole() {
        FestivalUser actor = new FestivalUser(ACTOR); actor.changeManagementRole(FestivalRole.ADMIN);
        when(repository.findByUserUuid(ACTOR)).thenReturn(Optional.of(actor));
        when(repository.findByUserUuidForUpdate(ACTOR)).thenReturn(Optional.of(actor));
        assertThatThrownBy(() -> service.updateManagementRole(ACTOR, FestivalRole.ADMIN, ACTOR, FestivalRole.USER))
                .isInstanceOfSatisfying(ApiException.class, error ->
                        assertThat(error.code()).isEqualTo("USER_ROLE_ESCALATION_FORBIDDEN"));
    }

    @Test void superAdminCanPromoteUserButCannotDemoteLastSuperAdmin() {
        FestivalUser user = new FestivalUser(TARGET);
        when(repository.findByUserUuid(TARGET)).thenReturn(Optional.of(user));
        when(repository.findByUserUuidForUpdate(TARGET)).thenReturn(Optional.of(user));
        assertThat(service.updateManagementRole(ACTOR, FestivalRole.SUPER_ADMIN, TARGET, FestivalRole.ADMIN))
                .containsExactly(FestivalRole.ADMIN);

        user.changeManagementRole(FestivalRole.SUPER_ADMIN);
        when(repository.findAllByManagementRoleForUpdate(FestivalRole.SUPER_ADMIN)).thenReturn(List.of(user));
        assertThatThrownBy(() -> service.updateManagementRole(ACTOR, FestivalRole.SUPER_ADMIN, TARGET, FestivalRole.USER))
                .isInstanceOfSatisfying(ApiException.class, error ->
                        assertThat(error.code()).isEqualTo("LAST_SUPER_ADMIN_REQUIRED"));
    }

    @Test void boothManagerCannotBeAssignedAsManagementRole() {
        assertThatThrownBy(() -> service.updateManagementRole(ACTOR, FestivalRole.SUPER_ADMIN,
                TARGET, FestivalRole.BOOTH_MANAGER)).isInstanceOfSatisfying(ApiException.class, error ->
                        assertThat(error.code()).isEqualTo("INVALID_MANAGEMENT_ROLE"));
    }
}
