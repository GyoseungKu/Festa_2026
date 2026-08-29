package org.syu_likelion.Festa_2026.bamboo;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.syu_likelion.Festa_2026.auth.AuthorizedSsoExecutor.AuthorizedResult;
import org.syu_likelion.Festa_2026.bamboo.BambooDtos.BambooMessageResponse;
import org.syu_likelion.Festa_2026.bamboo.BambooDtos.BambooNicknameResponse;
import org.syu_likelion.Festa_2026.bamboo.BambooDtos.BambooRoomResponse;
import org.syu_likelion.Festa_2026.bamboo.BambooDtos.BambooStreamResponse;
import org.syu_likelion.Festa_2026.user.UserDtos.MeResponse;
import org.syu_likelion.Festa_2026.user.UserService;

/**
 * SSO 인증을 붙여 {@link BambooService}를 호출한다.
 * 읽기와 쓰기 모두 로그인이 필요하므로 비로그인 경로는 없다.
 */
@Service
public class BambooApiService {
    private final BambooService bamboo;
    private final UserService users;
    private final BambooIdentityCache identities;

    public BambooApiService(BambooService bamboo, UserService users, BambooIdentityCache identities) {
        this.bamboo = bamboo;
        this.users = users;
        this.identities = identities;
    }

    /**
     * 사용자 식별자만 필요하므로 단기 캐시를 먼저 본다. 캐시가 맞으면 SSO 호출이 없고,
     * 토큰 로테이션도 일어나지 않으므로 응답 헤더에 담을 새 토큰도 없다.
     */
    private AuthorizedResult<UUID> authenticate(String access, String refresh) {
        UUID cached = identities.find(access);
        if (cached != null) return new AuthorizedResult<>(cached, null, null);
        AuthorizedResult<MeResponse> authenticated = users.getMe(access, refresh);
        UUID userUuid = authenticated.body().userUuid();
        // 토큰이 갱신됐다면 만료된 옛 토큰이 아니라 새 토큰을 캐시한다.
        String rotated = authenticated.newAccessToken();
        identities.store(rotated != null ? rotated : access, userUuid);
        return new AuthorizedResult<>(userUuid, rotated, authenticated.newRefreshToken());
    }

    public AuthorizedResult<BambooRoomResponse> room(String access, String refresh) {
        AuthorizedResult<UUID> authenticated = authenticate(access, refresh);
        return rotated(authenticated, bamboo.room(authenticated.body()));
    }

    public AuthorizedResult<BambooNicknameResponse> suggestNickname(String access, String refresh) {
        AuthorizedResult<UUID> authenticated = authenticate(access, refresh);
        return rotated(authenticated, bamboo.suggestNickname());
    }

    public AuthorizedResult<BambooNicknameResponse> claimNickname(String access, String refresh,
                                                                  String nickname) {
        AuthorizedResult<UUID> authenticated = authenticate(access, refresh);
        return rotated(authenticated, bamboo.claimNickname(authenticated.body(), nickname));
    }

    public AuthorizedResult<BambooStreamResponse> stream(String access, String refresh,
                                                          Long after, Integer size) {
        AuthorizedResult<UUID> authenticated = authenticate(access, refresh);
        return rotated(authenticated, bamboo.stream(authenticated.body(), after, size));
    }

    public AuthorizedResult<BambooStreamResponse> history(String access, String refresh,
                                                           Long before, Integer size) {
        AuthorizedResult<UUID> authenticated = authenticate(access, refresh);
        return rotated(authenticated, bamboo.history(authenticated.body(), before, size));
    }

    public AuthorizedResult<BambooMessageResponse> create(String access, String refresh, String content) {
        AuthorizedResult<UUID> authenticated = authenticate(access, refresh);
        return rotated(authenticated, bamboo.createAs(authenticated.body(), content));
    }

    public AuthorizedResult<Void> report(String access, String refresh, Long messageId,
                                         BambooReportReason reason) {
        AuthorizedResult<UUID> authenticated = authenticate(access, refresh);
        bamboo.reportAs(authenticated.body(), messageId, reason);
        return rotated(authenticated, null);
    }

    private <T> AuthorizedResult<T> rotated(AuthorizedResult<UUID> authenticated, T body) {
        return new AuthorizedResult<>(body, authenticated.newAccessToken(), authenticated.newRefreshToken());
    }
}
