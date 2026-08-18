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
import org.syu_likelion.Festa_2026.auth.AuthDtos.RecoveryEmailSendRequest;
import org.syu_likelion.Festa_2026.auth.AuthDtos.RecoveryPurpose;
import org.syu_likelion.Festa_2026.auth.AuthDtos.FindIdVerifyRequest;
import org.syu_likelion.Festa_2026.auth.AuthDtos.FindIdResponse;
import org.syu_likelion.Festa_2026.auth.AuthDtos.ResetPasswordRequest;
import org.syu_likelion.Festa_2026.error.ApiException;
import org.springframework.http.HttpStatus;
import org.syu_likelion.Festa_2026.sso.SsoAuthClient;
import org.syu_likelion.Festa_2026.sso.SsoException;
import org.syu_likelion.Festa_2026.sso.SsoResult;
import org.syu_likelion.Festa_2026.user.UserDtos.MeResponse;
import org.syu_likelion.Festa_2026.user.FestivalUserService;
import org.syu_likelion.Festa_2026.logging.ApiRequestContext;

@Service
public class AuthService {
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private final SsoAuthClient client;
    private final FestivalUserService festivalUsers;
    private final AccountRecoveryAttemptLimiter recoveryAttempts;
    private final WelcomeEmailService welcomeEmails;

    public AuthService(SsoAuthClient client, FestivalUserService festivalUsers,
                       AccountRecoveryAttemptLimiter recoveryAttempts,
                       WelcomeEmailService welcomeEmails) {
        this.client = client;
        this.festivalUsers = festivalUsers;
        this.recoveryAttempts = recoveryAttempts;
        this.welcomeEmails = welcomeEmails;
    }

    public void sendSignupEmailCode(EmailRequest request) { client.sendSignupEmailCode(request); }
    public void verifySignupEmailCode(EmailCodeRequest request) { client.verifySignupEmailCode(request); }

    public void sendRecoveryEmailCode(RecoveryEmailSendRequest request) {
        if (request.purpose() == RecoveryPurpose.RESET_PASSWORD
                && (request.loginId() == null || request.loginId().isBlank())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "LOGIN_ID_REQUIRED",
                    "비밀번호 재설정에는 로그인 아이디가 필요합니다.");
        }
        try {
            client.sendRecoveryEmailCode(request);
        } catch (SsoException exception) {
            if (exception.statusCode() != 404) throw exception;
            log.info("Account recovery email accepted upstreamUserFound=false purpose={} success=true",
                    request.purpose());
        }
    }

    public FindIdResponse findId(FindIdVerifyRequest request) {
        try {
            FindIdResponse response = client.verifyFindId(request);
            if (response == null || response.loginId() == null || response.loginId().isBlank()) {
                throw new SsoException(502, "SSO response did not contain loginId");
            }
            return response;
        } catch (SsoException exception) {
            if (exception.statusCode() == 404) throw recoveryFailed();
            throw exception;
        }
    }

    public void resetPassword(ResetPasswordRequest request) {
        recoveryAttempts.check(request.email());
        try {
            client.resetPassword(request);
            recoveryAttempts.clear(request.email());
        } catch (SsoException exception) {
            if (exception.statusCode() == 404) throw recoveryFailed();
            throw exception;
        }
    }

    private ApiException recoveryFailed() {
        return new ApiException(HttpStatus.BAD_REQUEST, "ACCOUNT_RECOVERY_FAILED",
                "입력 정보 또는 인증번호를 확인해 주세요.");
    }

    public SignupResponse signup(SignupRequest request) {
        SignupResponse response = client.register(request);
        if (response == null || response.userUuid() == null) throw new SsoException(502, "SSO response did not contain userUuid");
        festivalUsers.linkAndGetRoles(response.userUuid());
        welcomeEmails.sendLater(response.userUuid(), request.email(), request.name());
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
        welcomeEmails.sendLater(me.userUuid(), me.email(), me.name());
        ApiRequestContext.markAuthenticatedUser(me.userUuid());
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
