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
