package org.syu_likelion.Festa_2026.birthday;

import org.springframework.stereotype.Service;
import org.syu_likelion.Festa_2026.auth.AuthorizedSsoExecutor.AuthorizedResult;
import org.syu_likelion.Festa_2026.birthday.BirthdayMessageDtos.BirthdayMessageCreateRequest;
import org.syu_likelion.Festa_2026.birthday.BirthdayMessageDtos.BirthdayMessagePageResponse;
import org.syu_likelion.Festa_2026.birthday.BirthdayMessageDtos.BirthdayMessageResponse;
import org.syu_likelion.Festa_2026.birthday.BirthdayMessageDtos.HeartResponse;
import org.syu_likelion.Festa_2026.birthday.BirthdayMessageDtos.MyBirthdayMessageResponse;
import org.syu_likelion.Festa_2026.user.UserDtos.MeResponse;
import org.syu_likelion.Festa_2026.user.UserService;

@Service
public class BirthdayMessageApiService {
    private final BirthdayMessageService messages;
    private final UserService users;

    public BirthdayMessageApiService(BirthdayMessageService messages, UserService users) {
        this.messages = messages;
        this.users = users;
    }

    public AuthorizedResult<BirthdayMessagePageResponse> list(String access, String refresh,
                                                              BirthdayMessageSort sort, int page, int size, Long seed) {
        AuthorizedResult<MeResponse> authenticated = users.getMe(access, refresh);
        users.requireSchoolVerified(authenticated.body().userUuid());
        return rotated(authenticated, messages.list(authenticated.body().userUuid(), sort, page, size, seed));
    }

    public AuthorizedResult<BirthdayMessageResponse> get(Long id, String access, String refresh) {
        AuthorizedResult<MeResponse> authenticated = users.getMe(access, refresh);
        users.requireSchoolVerified(authenticated.body().userUuid());
        return rotated(authenticated, messages.get(id, authenticated.body().userUuid()));
    }

    public AuthorizedResult<MyBirthdayMessageResponse> getMine(String access, String refresh) {
        AuthorizedResult<MeResponse> authenticated = users.getMe(access, refresh);
        users.requireSchoolVerified(authenticated.body().userUuid());
        return rotated(authenticated, messages.getMine(authenticated.body().userUuid()));
    }

    public AuthorizedResult<BirthdayMessageResponse> create(String access, String refresh,
                                                             BirthdayMessageCreateRequest request) {
        AuthorizedResult<MeResponse> authenticated = users.getMe(access, refresh);
        users.requireSchoolVerified(authenticated.body().userUuid());
        MeResponse me = authenticated.body();
        return rotated(authenticated, messages.createAs(me.userUuid(), request.content(), me.department(),
                me.studentNo(), me.name(), request.designNo()));
    }

    public AuthorizedResult<Void> delete(Long id, String access, String refresh) {
        AuthorizedResult<MeResponse> authenticated = users.getMe(access, refresh);
        users.requireSchoolVerified(authenticated.body().userUuid());
        messages.deleteOwnAs(id, authenticated.body().userUuid());
        return rotated(authenticated, null);
    }

    public AuthorizedResult<HeartResponse> addHeart(Long id, String access, String refresh) {
        AuthorizedResult<MeResponse> authenticated = users.getMe(access, refresh);
        users.requireSchoolVerified(authenticated.body().userUuid());
        return rotated(authenticated, messages.addHeartAs(id, authenticated.body().userUuid()));
    }

    public AuthorizedResult<HeartResponse> removeHeart(Long id, String access, String refresh) {
        AuthorizedResult<MeResponse> authenticated = users.getMe(access, refresh);
        users.requireSchoolVerified(authenticated.body().userUuid());
        return rotated(authenticated, messages.removeHeartAs(id, authenticated.body().userUuid()));
    }

    private <T> AuthorizedResult<T> rotated(AuthorizedResult<MeResponse> authenticated, T body) {
        return new AuthorizedResult<>(body, authenticated.newAccessToken(), authenticated.newRefreshToken());
    }
}
