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
    private final BambooReportNotifier notifier;

    public BambooApiService(BambooService bamboo, UserService users,
                            BambooReportNotifier notifier) {
        this.bamboo = bamboo;
        this.users = users;
        this.notifier = notifier;
    }

    /**
     * 요청마다 SSO를 확인해 폐기된 토큰·계정 차단을 반영한다.
     * UserService의 요청 내부 중복 조회 방지와 정상 토큰 갱신은 유지한다.
     */
    private AuthorizedResult<UUID> authenticate(String access, String refresh) {
        AuthorizedResult<MeResponse> authenticated = users.getMe(access, refresh);
        UUID userUuid = authenticated.body().userUuid();
        users.requireSchoolVerified(userUuid);
        String rotated = authenticated.newAccessToken();
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
        long reportCount = bamboo.reportAs(authenticated.body(), messageId, reason);
        // 신고 트랜잭션이 끝난 뒤에 알린다. 알림 실패가 신고를 되돌리면 안 된다.
        notifier.notifyIfThresholdReached(messageId, reportCount);
        return rotated(authenticated, null);
    }

    private <T> AuthorizedResult<T> rotated(AuthorizedResult<UUID> authenticated, T body) {
        return new AuthorizedResult<>(body, authenticated.newAccessToken(), authenticated.newRefreshToken());
    }
}
