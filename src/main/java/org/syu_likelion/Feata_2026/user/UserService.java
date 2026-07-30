package org.syu_likelion.Feata_2026.user;

import org.springframework.stereotype.Service;
import org.syu_likelion.Feata_2026.auth.AuthorizedSsoExecutor;
import org.syu_likelion.Feata_2026.auth.AuthorizedSsoExecutor.AuthorizedResult;
import org.syu_likelion.Feata_2026.sso.SsoAuthClient;
import org.syu_likelion.Feata_2026.user.UserDtos.EmailCodeRequest;
import org.syu_likelion.Feata_2026.user.UserDtos.EmailRequest;
import org.syu_likelion.Feata_2026.user.UserDtos.MeResponse;
import org.syu_likelion.Feata_2026.user.UserDtos.PasswordChangeRequest;
import org.syu_likelion.Feata_2026.user.UserDtos.ProfileUpdateRequest;

@Service
public class UserService {
    private final SsoAuthClient client;
    private final AuthorizedSsoExecutor executor;
    private final FestivalUserService festivalUsers;

    public UserService(SsoAuthClient client, AuthorizedSsoExecutor executor, FestivalUserService festivalUsers) {
        this.client = client;
        this.executor = executor;
        this.festivalUsers = festivalUsers;
    }

    public AuthorizedResult<MeResponse> getMe(String access, String refresh) {
        AuthorizedResult<MeResponse> result = executor.execute(access, refresh, client::getMe);
        return withRoles(result);
    }

    public AuthorizedResult<MeResponse> updateProfile(String access, String refresh, ProfileUpdateRequest request) {
        AuthorizedResult<MeResponse> result = executor.execute(access, refresh, token -> {
            client.updateProfile(token, request);
            return client.getMe(token);
        });
        return withRoles(result);
    }

    public AuthorizedResult<Void> sendNewEmailCode(String access, String refresh, EmailRequest request) {
        return executor.execute(access, refresh, token -> { client.sendNewEmailCode(token, request); return null; });
    }

    public AuthorizedResult<Void> verifyNewEmailCode(String access, String refresh, EmailCodeRequest request) {
        return executor.execute(access, refresh, token -> { client.verifyNewEmailCode(token, request); return null; });
    }

    public AuthorizedResult<Void> changeEmail(String access, String refresh, EmailRequest request) {
        return executor.execute(access, refresh, token -> { client.changeEmail(token, request); return null; });
    }

    public AuthorizedResult<Void> changePassword(String access, String refresh, PasswordChangeRequest request) {
        return executor.execute(access, refresh, token -> { client.changePassword(token, request); return null; });
    }

    public AuthorizedResult<Void> withdraw(String access, String refresh) {
        return executor.execute(access, refresh, token -> { client.withdraw(token); return null; });
    }

    private AuthorizedResult<MeResponse> withRoles(AuthorizedResult<MeResponse> result) {
        MeResponse me = result.body();
        MeResponse enriched = me.withFestivalRoles(festivalUsers.linkAndGetRoles(me.userUuid()));
        return new AuthorizedResult<>(enriched, result.newAccessToken(), result.newRefreshToken());
    }
}
