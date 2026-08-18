package org.syu_likelion.Festa_2026.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.syu_likelion.Festa_2026.auth.AuthorizedSsoExecutor.AuthorizedResult;
import org.syu_likelion.Festa_2026.error.ApiException;
import org.syu_likelion.Festa_2026.user.FestivalRole;
import org.syu_likelion.Festa_2026.user.UserDtos.MeResponse;
import org.syu_likelion.Festa_2026.user.UserService;

/** Authenticates protected multipart endpoints before Spring parses their request bodies. */
public final class EarlyMultipartAuthenticationFilter extends OncePerRequestFilter {
    private static final Pattern BOOTH_MEDIA = Pattern.compile(
            "^/api/booths/[^/]+/(?:images|videos)/?$");
    private static final Pattern PERFORMANCE_MEDIA = Pattern.compile(
            "^/api/performances/[^/]+/(?:images|videos)/?$");
    private static final Pattern POLL_OPTION_IMAGE = Pattern.compile(
            "^/api/admin/polls/[^/]+/options/[^/]+/image/?$");
    private static final Pattern POLL_QUESTION_MEDIA = Pattern.compile(
            "^/api/admin/polls/[^/]+/questions/[^/]+/media/?$");
    private static final Pattern LOST_ITEM_DETAIL = Pattern.compile("^/api/lost-items/[^/]+/?$");

    private final UserService users;
    private final TokenCookieManager cookies;
    private final HandlerExceptionResolver exceptions;

    public EarlyMultipartAuthenticationFilter(UserService users, TokenCookieManager cookies,
                                               HandlerExceptionResolver exceptions) {
        this.users = users;
        this.cookies = cookies;
        this.exceptions = exceptions;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return requiredAccess(request) == null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        RequiredAccess required = requiredAccess(request);
        try {
            String access = BearerTokens.require(request.getHeader("Authorization"));
            AuthorizedResult<MeResponse> authenticated = users.authenticateEarly(
                    request, access, cookies.readRefreshToken(request));
            requireRole(authenticated.body().festivalRoles(), required);
        } catch (RuntimeException authenticationFailure) {
            exceptions.resolveException(request, response, null, authenticationFailure);
            return;
        }
        chain.doFilter(request, response);
    }

    private RequiredAccess requiredAccess(HttpServletRequest request) {
        String contextPath = request.getContextPath();
        String uri = request.getRequestURI();
        String path = contextPath == null || contextPath.isEmpty() ? uri : uri.substring(contextPath.length());
        String method = request.getMethod();
        if ("POST".equals(method) && (BOOTH_MEDIA.matcher(path).matches()
                || PERFORMANCE_MEDIA.matcher(path).matches()
                || POLL_OPTION_IMAGE.matcher(path).matches()
                || POLL_QUESTION_MEDIA.matcher(path).matches())) return RequiredAccess.ADMIN;
        if ("POST".equals(method) && "/api/lost-items".equals(stripTrailingSlash(path)))
            return RequiredAccess.STAFF;
        if ("PATCH".equals(method) && LOST_ITEM_DETAIL.matcher(path).matches()) return RequiredAccess.STAFF;
        return null;
    }

    private String stripTrailingSlash(String path) {
        return path.length() > 1 && path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
    }

    private void requireRole(Set<FestivalRole> roles, RequiredAccess required) {
        boolean allowed = roles != null && switch (required) {
            case ADMIN -> roles.contains(FestivalRole.ADMIN) || roles.contains(FestivalRole.SUPER_ADMIN);
            case STAFF -> roles.contains(FestivalRole.STAFF) || roles.contains(FestivalRole.ADMIN)
                    || roles.contains(FestivalRole.SUPER_ADMIN);
        };
        if (!allowed) throw new ApiException(HttpStatus.FORBIDDEN, "MULTIPART_MANAGE_FORBIDDEN",
                "이 파일 업로드를 수행할 권한이 없습니다.");
    }

    private enum RequiredAccess { STAFF, ADMIN }
}
