package org.syu_likelion.Festa_2026.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.syu_likelion.Festa_2026.auth.AuthDtos.EmailCodeRequest;
import org.syu_likelion.Festa_2026.auth.AuthDtos.EmailRequest;
import org.syu_likelion.Festa_2026.auth.AuthDtos.LoginRequest;
import org.syu_likelion.Festa_2026.auth.AuthDtos.SignupRequest;
import org.syu_likelion.Festa_2026.auth.AuthDtos.SignupResponse;
import org.syu_likelion.Festa_2026.auth.AuthDtos.TokenResponse;
import org.syu_likelion.Festa_2026.sso.SsoAuthClient;
import org.syu_likelion.Festa_2026.sso.SsoException;
import org.syu_likelion.Festa_2026.sso.SsoResult;
import org.syu_likelion.Festa_2026.user.UserDtos.MeResponse;
import org.syu_likelion.Festa_2026.user.FestivalUserService;

@Service
public class AuthService {
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private final SsoAuthClient client;
    private final FestivalUserService festivalUsers;

    public AuthService(SsoAuthClient client, FestivalUserService festivalUsers) {
        this.client = client;
        this.festivalUsers = festivalUsers;
    }

    public void sendSignupEmailCode(EmailRequest request) { client.sendSignupEmailCode(request); }
    public void verifySignupEmailCode(EmailCodeRequest request) { client.verifySignupEmailCode(request); }

    public SignupResponse signup(SignupRequest request) {
        SignupResponse response = client.register(request);
        if (response == null || response.userUuid() == null) throw new SsoException(502, "SSO response did not contain userUuid");
        festivalUsers.linkAndGetRoles(response.userUuid());
        return response;
    }

    public LoginResult login(LoginRequest request) {
        SsoResult<TokenResponse> result = client.login(request);
        TokenRefreshCoordinator.requireAccessToken(result.body());
        if (result.refreshToken() == null || result.refreshToken().isBlank()) {
            throw new SsoException(502, "SSO response did not contain refresh token");
        }
        MeResponse me = client.getMe(result.body().accessToken());
        festivalUsers.linkAndGetRoles(me.userUuid());
        return new LoginResult(result.body(), result.refreshToken());
    }

    public void logout(String accessToken) {
        try {
            client.logout(accessToken);
        } catch (SsoException exception) {
            log.warn("SSO logout failed status={} localRefreshCookieCleared=true", exception.statusCode());
        }
    }

    public record LoginResult(TokenResponse tokens, String refreshToken) { }
}
