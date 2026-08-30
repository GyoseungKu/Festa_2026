package org.syu_likelion.Festa_2026.schoolsso;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.util.UriComponentsBuilder;
import org.syu_likelion.Festa_2026.error.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.syu_likelion.Festa_2026.auth.AuthorizedSsoExecutor.AuthorizedResult;
import org.syu_likelion.Festa_2026.auth.BearerTokens;
import org.syu_likelion.Festa_2026.auth.TokenCookieManager;
import org.syu_likelion.Festa_2026.user.FestivalUserService;
import org.syu_likelion.Festa_2026.user.UserController;
import org.syu_likelion.Festa_2026.user.UserDtos.MeResponse;
import org.syu_likelion.Festa_2026.user.UserService;
import org.syu_likelion.Festa_2026.schoolsso.SchoolSsoSessionStore.PendingAuthorization;
import org.syu_likelion.Festa_2026.schoolsso.SchoolSsoSessionStore.AuthorizationFlow;

@Controller
public class SchoolSsoController {
    private static final Logger log = LoggerFactory.getLogger(SchoolSsoController.class);
    private final SecureRandom random = new SecureRandom();
    private final SchoolSsoProperties properties;
    private final SchoolSsoClient client;
    private final SchoolSsoSessionStore sessions;
    private final UserService users;
    private final FestivalUserService festivalUsers;
    private final TokenCookieManager cookies;

    public SchoolSsoController(SchoolSsoProperties properties, SchoolSsoClient client,
                               SchoolSsoSessionStore sessions, UserService users,
                               FestivalUserService festivalUsers, TokenCookieManager cookies) {
        this.properties = properties;
        this.client = client;
        this.sessions = sessions;
        this.users = users;
        this.festivalUsers = festivalUsers;
        this.cookies = cookies;
    }

    @GetMapping("/api/auth/school/authorize")
    String authorize(HttpServletRequest request, HttpServletResponse response) {
        noStore(response);
        client.requireConfigured();
        byte[] randomBytes = new byte[32];
        random.nextBytes(randomBytes);
        String state = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        sessions.saveState(request, state);
        return "redirect:" + authorizeUrl(state);
    }

    @PostMapping("/api/users/me/school-verification/authorize")
    @ResponseBody
    ResponseEntity<SchoolAuthorizationResponse> authorizeAccount(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            HttpServletRequest request, HttpServletResponse servletResponse) {
        noStore(servletResponse);
        client.requireConfigured();
        AuthorizedResult<MeResponse> authenticated = users.getMe(BearerTokens.require(authorization),
                cookies.readRefreshToken(request));
        byte[] randomBytes = new byte[32];
        random.nextBytes(randomBytes);
        String state = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        sessions.saveAccountState(request, state, authenticated.body().userUuid());
        ResponseEntity.BodyBuilder response = ResponseEntity.ok().cacheControl(CacheControl.noStore());
        if (authenticated.newAccessToken() != null)
            response.header(UserController.REFRESHED_ACCESS_TOKEN, authenticated.newAccessToken());
        if (authenticated.newRefreshToken() != null)
            response.header(TokenCookieManager.SET_COOKIE, cookies.create(authenticated.newRefreshToken()));
        return response.body(new SchoolAuthorizationResponse(authorizeUrl(state)));
    }

    @GetMapping("/auth/sso/callback")
    String callback(@RequestParam(required = false) String code,
                    @RequestParam(required = false) String state,
                    @RequestParam(required = false) String error,
                    HttpServletRequest request,
                    HttpServletResponse response) {
        noStore(response);
        AuthorizationFlow requestedFlow = sessions.currentFlow(request);
        PendingAuthorization pending = sessions.consumeAuthorization(request, state);
        if (pending == null) return requestedFlow == AuthorizationFlow.ACCOUNT_VERIFICATION
                ? resultRedirect(null, "invalid_state") : resultRedirect("invalid_state", null);
        if (error != null) {
            String result = "access_denied".equals(error) ? "access_denied" : "failed";
            return pending.flow() == AuthorizationFlow.ACCOUNT_VERIFICATION
                    ? resultRedirect(null, result) : resultRedirect(result, null);
        }
        try {
            SchoolAcademicProfile profile = client.exchangeAndVerify(code);
            if (pending.flow() == AuthorizationFlow.ACCOUNT_VERIFICATION) {
                festivalUsers.verifySchool(pending.userUuid(), profile);
                return resultRedirect(null, "success");
            }
            sessions.saveProfile(request, profile);
            return resultRedirect("success", null);
        } catch (ApiException exception) {
            log.warn("School SSO callback failed code={} status={} success=false",
                    exception.code(), exception.status().value());
            if (pending.flow() == AuthorizationFlow.ACCOUNT_VERIFICATION
                    && "SCHOOL_IDENTITY_ALREADY_LINKED".equals(exception.code())) {
                return resultRedirect(null, "already_linked");
            }
            return pending.flow() == AuthorizationFlow.ACCOUNT_VERIFICATION
                    ? resultRedirect(null, "failed") : resultRedirect("failed", null);
        }
    }

    @GetMapping("/api/auth/school/profile")
    @ResponseBody
    ResponseEntity<AcademicProfileResponse> profile(HttpServletRequest request) {
        SchoolAcademicProfile profile = sessions.requireProfile(request);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new AcademicProfileResponse(profile.studentNo(), profile.department(),
                        profile.name(), profile.consentTarget(), profile.expiresAt()));
    }

    @DeleteMapping("/api/auth/school/profile")
    @ResponseBody
    ResponseEntity<Void> clear(HttpServletRequest request) {
        sessions.clearProfile(request);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    private String resultRedirect(String result) { return resultRedirect(result, null); }

    private String resultRedirect(String signupResult, String verificationResult) {
        String location = UriComponentsBuilder.fromUriString(properties.returnUrl())
                .queryParamIfPresent("schoolSso", java.util.Optional.ofNullable(signupResult))
                .queryParamIfPresent("schoolVerification", java.util.Optional.ofNullable(verificationResult))
                .build().encode().toUriString();
        return "redirect:" + location;
    }

    private String authorizeUrl(String state) {
        return UriComponentsBuilder.fromUriString(properties.authorizeUrl())
                .queryParam("client_id", properties.clientId())
                .queryParam("redirect_uri", properties.callbackUrl())
                .queryParam("state", state).build().encode().toUriString();
    }

    private void noStore(HttpServletResponse response) {
        if (response == null) return;
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Pragma", "no-cache");
        response.setHeader("Referrer-Policy", "no-referrer");
    }

    public record AcademicProfileResponse(String studentNo, String department, String name,
                                          String consentTarget, java.time.Instant expiresAt) { }
    public record SchoolAuthorizationResponse(String authorizeUrl) { }
}
