package org.syu_likelion.Festa_2026.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.FilterChain;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.syu_likelion.Festa_2026.auth.AuthorizedSsoExecutor.AuthorizedResult;
import org.syu_likelion.Festa_2026.error.ApiException;
import org.syu_likelion.Festa_2026.user.FestivalRole;
import org.syu_likelion.Festa_2026.user.UserDtos.MeResponse;
import org.syu_likelion.Festa_2026.user.UserService;

class EarlyMultipartAuthenticationFilterTests {
    private final UserService users = mock(UserService.class);
    private final TokenCookieManager cookies = mock(TokenCookieManager.class);
    private final HandlerExceptionResolver exceptions = mock(HandlerExceptionResolver.class);
    private final EarlyMultipartAuthenticationFilter filter =
            new EarlyMultipartAuthenticationFilter(users, cookies, exceptions);

    @Test
    void rejectsMissingBearerBeforeBoothMultipartContinues() throws Exception {
        MockHttpServletRequest request = request("POST", "/api/booths/1/media/images");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain, never()).doFilter(any(), any());
        verify(users, never()).getMe(any(), any());
        verify(exceptions).resolveException(eq(request), eq(response), isNull(), isA(ApiException.class));
    }

    @Test
    void permitsAdminBoothMediaRequestAfterEarlyAuthentication() throws Exception {
        MockHttpServletRequest request = request("POST", "/api/booths/1/media/videos");
        request.addHeader("Authorization", "Bearer valid-access");
        FilterChain chain = mock(FilterChain.class);
        authenticateAs(FestivalRole.ADMIN);

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        verify(chain).doFilter(any(), any());
    }

    @Test
    void rejectsStaffFromAdminOnlyPerformanceUpload() throws Exception {
        MockHttpServletRequest request = request("POST", "/api/performances/1/media/images");
        request.addHeader("Authorization", "Bearer staff-access");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        authenticateAs(FestivalRole.STAFF);

        filter.doFilter(request, response, chain);

        verify(chain, never()).doFilter(any(), any());
        verify(exceptions).resolveException(eq(request), eq(response), isNull(), isA(ApiException.class));
    }

    @Test
    void permitsStaffLostItemCreateAndUpdate() throws Exception {
        authenticateAs(FestivalRole.STAFF);
        FilterChain createChain = mock(FilterChain.class);
        FilterChain updateChain = mock(FilterChain.class);
        MockHttpServletRequest create = authorized("POST", "/api/lost-items");
        MockHttpServletRequest update = authorized("PATCH", "/api/lost-items/7");

        filter.doFilter(create, new MockHttpServletResponse(), createChain);
        filter.doFilter(update, new MockHttpServletResponse(), updateChain);

        verify(createChain).doFilter(any(), any());
        verify(updateChain).doFilter(any(), any());
    }

    @Test
    void leavesNonMultipartApiOutsideEarlyAuthentication() throws Exception {
        MockHttpServletRequest request = request("GET", "/api/booths");
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        verify(chain).doFilter(any(), any());
        verify(users, never()).getMe(any(), any());
    }

    private void authenticateAs(FestivalRole role) {
        MeResponse me = mock(MeResponse.class);
        when(me.festivalRoles()).thenReturn(Set.of(role));
        when(users.authenticateEarly(any(), any(), any()))
                .thenReturn(new AuthorizedResult<>(me, null, null));
    }

    private MockHttpServletRequest authorized(String method, String path) {
        MockHttpServletRequest request = request(method, path);
        request.addHeader("Authorization", "Bearer valid-access");
        return request;
    }

    private MockHttpServletRequest request(String method, String path) {
        return new MockHttpServletRequest(method, path);
    }
}
