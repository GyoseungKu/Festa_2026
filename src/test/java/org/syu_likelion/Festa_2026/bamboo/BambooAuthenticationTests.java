package org.syu_likelion.Festa_2026.bamboo;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import org.syu_likelion.Festa_2026.auth.AuthorizedSsoExecutor.AuthorizedResult;
import org.syu_likelion.Festa_2026.sso.SsoException;
import org.syu_likelion.Festa_2026.user.UserService;
import org.syu_likelion.Festa_2026.user.UserDtos.MeResponse;

class BambooAuthenticationTests {
    @ParameterizedTest
    @ValueSource(ints = {401, 403})
    void revokedOrBlockedTokenIsRejectedOnEveryRouteAfterSuccessfulRequest(int status) {
        var users = mock(UserService.class);
        var bamboo = mock(BambooService.class);
        var notifier = mock(BambooReportNotifier.class);
        var api = new BambooApiService(bamboo, users, notifier);
        var me = mock(MeResponse.class);
        when(me.userUuid()).thenReturn(UUID.randomUUID());
        when(users.getMe("token", null)).thenReturn(new AuthorizedResult<>(me, null, null));
        api.room("token", null);
        clearInvocations(bamboo, users);
        when(users.getMe("token", null)).thenThrow(new SsoException(status, "revoked or blocked"));
        List<Runnable> calls = List.of(() -> api.room("token", null),
                () -> api.suggestNickname("token", null), () -> api.claimNickname("token", null, "졸린 오리"),
                () -> api.stream("token", null, null, 20), () -> api.history("token", null, 10L, 20),
                () -> api.create("token", null, "안녕하세요"),
                () -> api.report("token", null, 1L, BambooReportReason.SPAM));
        for (Runnable call : calls) {
            assertThatThrownBy(call::run).isInstanceOfSatisfying(SsoException.class,
                    e -> assertThat(e.statusCode()).isEqualTo(status));
        }
        verifyNoInteractions(bamboo, notifier);
    }

    @Test void revalidatesSameTokenOnNextRequestAndPreservesRotation() {
        var users = mock(UserService.class);
        var bamboo = mock(BambooService.class);
        var api = new BambooApiService(bamboo, users, mock(BambooReportNotifier.class));
        var me = mock(MeResponse.class);
        UUID id = UUID.randomUUID();
        when(me.userUuid()).thenReturn(id);
        when(users.getMe("old", "refresh")).thenReturn(new AuthorizedResult<>(me, "new", "new-refresh"));
        for (int i = 0; i < 2; i++) {
            var result = api.room("old", "refresh");
            assertThat(result.newAccessToken()).isEqualTo("new");
            assertThat(result.newRefreshToken()).isEqualTo("new-refresh");
        }
        verify(users, times(2)).getMe("old", "refresh");
        verify(users, times(2)).requireSchoolVerified(id);
    }
}
