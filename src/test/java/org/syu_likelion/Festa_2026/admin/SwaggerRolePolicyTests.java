package org.syu_likelion.Festa_2026.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.syu_likelion.Festa_2026.auth.AuthorizedSsoExecutor.AuthorizedResult;
import org.syu_likelion.Festa_2026.error.ApiException;
import org.syu_likelion.Festa_2026.user.FestivalRole;
import org.syu_likelion.Festa_2026.user.UserDtos.MeResponse;
import org.syu_likelion.Festa_2026.user.UserService;

class SwaggerRolePolicyTests {
    private final UserService users = mock(UserService.class);
    private final AdminAccessService access = new AdminAccessService(users);

    @ParameterizedTest
    @EnumSource(FestivalRole.class)
    void eachExplicitlyAllowedRoleCanReadSwagger(FestivalRole role) {
        profile(Set.of(role));
        assertThat(access.authenticateForSwagger("token", null).body().roles()).contains(role);
        verify(users).getMe("token", null);
    }

    @Test void swaggerMemberCannotAccessAdminWorkflows() {
        profile(Set.of(FestivalRole.USER));
        access.authenticateForSwagger("token", null);
        assertThatThrownBy(() -> access.authenticate("token", null))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("ADMIN_ROLE_REQUIRED"));
    }

    @Test void absentOrUnlistedRolesFailClosed() {
        profile(Set.of());
        assertThatThrownBy(() -> access.authenticateForSwagger("token", null))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("SWAGGER_ROLE_REQUIRED"));
        profile(null);
        assertThatThrownBy(() -> access.authenticateForSwagger("token", null)).isInstanceOf(ApiException.class);
    }

    @Test void anonymousAccessNeverReachesSso() {
        assertThatThrownBy(() -> access.authenticateForSwagger(null, null)).isInstanceOf(ApiException.class);
        verifyNoInteractions(users);
    }

    private void profile(Set<FestivalRole> roles) {
        MeResponse me = mock(MeResponse.class);
        when(me.userUuid()).thenReturn(UUID.randomUUID());
        when(me.festivalRoles()).thenReturn(roles);
        when(users.getMe("token", null)).thenReturn(new AuthorizedResult<>(me, null, null));
    }
}
