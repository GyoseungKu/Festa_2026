package org.syu_likelion.Festa_2026.user;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.syu_likelion.Festa_2026.auth.AuthorizedSsoExecutor;
import org.syu_likelion.Festa_2026.auth.AuthorizedSsoExecutor.AuthorizedResult;
import org.syu_likelion.Festa_2026.sso.SsoAuthClient;
import org.syu_likelion.Festa_2026.user.UserDtos.EmailCodeRequest;
import org.syu_likelion.Festa_2026.user.UserDtos.EmailRequest;
import org.syu_likelion.Festa_2026.user.UserDtos.MeResponse;
import org.syu_likelion.Festa_2026.user.UserDtos.PasswordChangeRequest;
import org.syu_likelion.Festa_2026.user.UserDtos.ProfileUpdateRequest;
import org.syu_likelion.Festa_2026.logging.ApiRequestContext;
import org.syu_likelion.Festa_2026.error.ApiException;
import org.springframework.http.HttpStatus;

@Service
public class UserService {
    private static final String AUTH_CACHE = UserService.class.getName() + ".authenticatedUser";
    private final SsoAuthClient client;
    private final AuthorizedSsoExecutor executor;
    private final FestivalUserService festivalUsers;

    public UserService(SsoAuthClient client, AuthorizedSsoExecutor executor, FestivalUserService festivalUsers) {
        this.client = client;
        this.executor = executor;
        this.festivalUsers = festivalUsers;
    }

    /** 참여 기능은 SSO 식별 캐시와 별개로 현재 축제 DB의 학생 인증을 확인한다. */
    public void requireSchoolVerified(java.util.UUID userUuid) {
        if (!festivalUsers.getProfile(userUuid).schoolVerified()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "SCHOOL_VERIFICATION_REQUIRED",
                    "학생 인증 완료 후 이용할 수 있습니다.");
        }
    }

    public AuthorizedResult<MeResponse> getMe(String access, String refresh) {
        CachedAuthentication cached = cachedAuthentication();
        if (cached != null && Objects.equals(cached.access(), access)
                && Objects.equals(cached.refresh(), refresh)) return cached.result();
        AuthorizedResult<MeResponse> result = executor.execute(access, refresh, client::getMe);
        AuthorizedResult<MeResponse> enriched = withRoles(result);
        cacheAuthentication(new CachedAuthentication(access, refresh, enriched));
        return enriched;
    }

    public AuthorizedResult<MeResponse> authenticateEarly(HttpServletRequest request,
                                                           String access, String refresh) {
        AuthorizedResult<MeResponse> result = getMe(access, refresh);
        request.setAttribute(AUTH_CACHE, new CachedAuthentication(access, refresh, result));
        return result;
    }

    public AuthorizedResult<MeResponse> updateProfile(String access, String refresh, ProfileUpdateRequest request) {
        if (request == null || request.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PROFILE_UPDATE_REQUIRED",
                    "변경할 개인정보를 하나 이상 입력해 주세요.");
        }
        AuthorizedResult<MeResponse> result = executor.execute(access, refresh, token -> {
            client.updateProfile(token, request);
            return client.getMe(token);
        });
        if (request.department() != null) {
            festivalUsers.revokeSchoolVerification(result.body().userUuid());
        }
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

    public java.util.UUID authenticateForWithdrawal(String access, String refresh) {
        // Verify with SSO without creating a local user or sending a welcome email.
        return executor.execute(access, refresh, client::getMe).body().userUuid();
    }

    private AuthorizedResult<MeResponse> withRoles(AuthorizedResult<MeResponse> result) {
        MeResponse me = result.body();
        ApiRequestContext.markAuthenticatedUser(me.userUuid());
        MeResponse enriched = me.withFestivalProfile(festivalUsers.linkAndGetProfile(me.userUuid()));
        return new AuthorizedResult<>(enriched, result.newAccessToken(), result.newRefreshToken());
    }

    private CachedAuthentication cachedAuthentication() {
        ServletRequestAttributes attributes = requestAttributes();
        return attributes == null ? null
                : (CachedAuthentication) attributes.getRequest().getAttribute(AUTH_CACHE);
    }

    private void cacheAuthentication(CachedAuthentication authentication) {
        ServletRequestAttributes attributes = requestAttributes();
        if (attributes != null) attributes.getRequest().setAttribute(AUTH_CACHE, authentication);
    }

    private ServletRequestAttributes requestAttributes() {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes servlet
                ? servlet : null;
    }

    private record CachedAuthentication(String access, String refresh,
                                        AuthorizedResult<MeResponse> result) { }
}
