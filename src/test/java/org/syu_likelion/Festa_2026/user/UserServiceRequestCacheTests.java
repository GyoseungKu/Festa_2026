package org.syu_likelion.Festa_2026.user;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.syu_likelion.Festa_2026.auth.AuthorizedSsoExecutor;
import org.syu_likelion.Festa_2026.auth.AuthorizedSsoExecutor.AuthorizedResult;
import org.syu_likelion.Festa_2026.sso.SsoAuthClient;
import org.syu_likelion.Festa_2026.user.UserDtos.MeResponse;

class UserServiceRequestCacheTests {
    @AfterEach
    void clearRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void reusesAuthenticationWithinSameHttpRequest() {
        SsoAuthClient client = mock(SsoAuthClient.class);
        AuthorizedSsoExecutor executor = mock(AuthorizedSsoExecutor.class);
        FestivalUserService festivalUsers = mock(FestivalUserService.class);
        UserService service = new UserService(client, executor, festivalUsers);
        MeResponse raw = mock(MeResponse.class);
        MeResponse enriched = mock(MeResponse.class);
        UUID userUuid = UUID.randomUUID();
        when(raw.userUuid()).thenReturn(userUuid);
        when(raw.withFestivalRoles(any())).thenReturn(enriched);
        when(festivalUsers.linkAndGetRoles(userUuid)).thenReturn(Set.of(FestivalRole.ADMIN));
        when(executor.execute(eq("access"), eq("refresh"), any()))
                .thenReturn(new AuthorizedResult<>(raw, null, null));
        MockHttpServletRequest request = new MockHttpServletRequest();

        AuthorizedResult<MeResponse> first = service.authenticateEarly(request, "access", "refresh");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        AuthorizedResult<MeResponse> second = service.getMe("access", "refresh");

        org.assertj.core.api.Assertions.assertThat(second).isSameAs(first);
        verify(executor, times(1)).execute(eq("access"), eq("refresh"), any());
        verify(festivalUsers, times(1)).linkAndGetRoles(userUuid);
    }
}
