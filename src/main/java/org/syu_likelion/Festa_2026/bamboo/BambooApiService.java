package org.syu_likelion.Festa_2026.bamboo;

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

    public BambooApiService(BambooService bamboo, UserService users) {
        this.bamboo = bamboo;
        this.users = users;
    }

    public AuthorizedResult<BambooRoomResponse> room(String access, String refresh) {
        AuthorizedResult<MeResponse> authenticated = users.getMe(access, refresh);
        return rotated(authenticated, bamboo.room(authenticated.body().userUuid()));
    }

    public AuthorizedResult<BambooNicknameResponse> suggestNickname(String access, String refresh) {
        AuthorizedResult<MeResponse> authenticated = users.getMe(access, refresh);
        return rotated(authenticated, bamboo.suggestNickname());
    }

    public AuthorizedResult<BambooNicknameResponse> claimNickname(String access, String refresh,
                                                                  String nickname) {
        AuthorizedResult<MeResponse> authenticated = users.getMe(access, refresh);
        return rotated(authenticated, bamboo.claimNickname(authenticated.body().userUuid(), nickname));
    }

    public AuthorizedResult<BambooStreamResponse> stream(String access, String refresh,
                                                          Long after, Integer size) {
        AuthorizedResult<MeResponse> authenticated = users.getMe(access, refresh);
        return rotated(authenticated, bamboo.stream(authenticated.body().userUuid(), after, size));
    }

    public AuthorizedResult<BambooStreamResponse> history(String access, String refresh,
                                                           Long before, Integer size) {
        AuthorizedResult<MeResponse> authenticated = users.getMe(access, refresh);
        return rotated(authenticated, bamboo.history(authenticated.body().userUuid(), before, size));
    }

    public AuthorizedResult<BambooMessageResponse> create(String access, String refresh, String content) {
        AuthorizedResult<MeResponse> authenticated = users.getMe(access, refresh);
        return rotated(authenticated, bamboo.createAs(authenticated.body().userUuid(), content));
    }

    private <T> AuthorizedResult<T> rotated(AuthorizedResult<MeResponse> authenticated, T body) {
        return new AuthorizedResult<>(body, authenticated.newAccessToken(), authenticated.newRefreshToken());
    }
}
