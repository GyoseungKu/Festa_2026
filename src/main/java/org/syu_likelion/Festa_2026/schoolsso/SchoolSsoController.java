package org.syu_likelion.Festa_2026.schoolsso;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
import org.syu_likelion.Festa_2026.user.UserDtos.ProfileUpdateRequest;
import org.syu_likelion.Festa_2026.user.SchoolVerificationApprovalService;
import org.syu_likelion.Festa_2026.schoolsso.SchoolSsoSessionStore.PendingAuthorization;
import org.syu_likelion.Festa_2026.schoolsso.SchoolSsoSessionStore.AuthorizationFlow;

@Controller
@Tag(name = "School SSO", description = "학교 SSO 학적정보와 학생 인증")
public class SchoolSsoController {
    private static final Logger log = LoggerFactory.getLogger(SchoolSsoController.class);
    private final SecureRandom random = new SecureRandom();
    private final SchoolSsoProperties properties;
    private final SchoolSsoClient client;
    private final SchoolSsoSessionStore sessions;
    private final UserService users;
    private final FestivalUserService festivalUsers;
    private final TokenCookieManager cookies;
    private final SchoolVerificationApprovalService approvals;

    public SchoolSsoController(SchoolSsoProperties properties, SchoolSsoClient client,
                               SchoolSsoSessionStore sessions, UserService users,
                               FestivalUserService festivalUsers, TokenCookieManager cookies,
                               SchoolVerificationApprovalService approvals) {
        this.properties = properties;
        this.client = client;
        this.sessions = sessions;
        this.users = users;
        this.festivalUsers = festivalUsers;
        this.cookies = cookies;
        this.approvals = approvals;
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
    @Operation(summary = "가입 후 학생 인증 시작",
            description = "현재 회원정보를 기준으로 학교 SSO 인증 URL을 발급합니다.",
            security = @SecurityRequirement(name = "bearerAuth"))
    ResponseEntity<SchoolAuthorizationResponse> authorizeAccount(
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authorization,
            @Parameter(hidden = true) HttpServletRequest request,
            @Parameter(hidden = true) HttpServletResponse servletResponse) {
        noStore(servletResponse);
        client.requireConfigured();
        AuthorizedResult<MeResponse> authenticated = users.getMe(BearerTokens.require(authorization),
                cookies.readRefreshToken(request));
        byte[] randomBytes = new byte[32];
        random.nextBytes(randomBytes);
        String state = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        MeResponse me = authenticated.body();
        sessions.saveAccountState(request, state, me.userUuid(), me.name(), me.studentNo(), me.department());
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
                if (!sameName(pending.name(), profile.name())
                        || !sameStudentNo(pending.studentNo(), profile.studentNo())) {
                    approvals.request(pending.userUuid(), value(pending.name()), value(pending.studentNo()),
                            pending.department(), profile);
                    return resultRedirect(null, "pending_approval");
                }
                if (!sameDepartment(pending.department(), profile.department())) {
                    approvals.clear(pending.userUuid());
                    sessions.saveAccountProfile(request, pending.userUuid(), profile);
                    return resultRedirect(null, "department_update_required");
                }
                approvals.clear(pending.userUuid());
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
    @Operation(summary = "회원가입용 학교 학적정보 조회",
            description = "학교 SSO 인증 후 세션에 임시 보관된 학적정보를 조회합니다.")
    ResponseEntity<AcademicProfileResponse> profile(@Parameter(hidden = true) HttpServletRequest request) {
        SchoolAcademicProfile profile = sessions.requireProfile(request);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new AcademicProfileResponse(profile.studentNo(), profile.department(),
                        profile.name(), profile.consentTarget(), profile.expiresAt()));
    }

    @DeleteMapping("/api/auth/school/profile")
    @ResponseBody
    @Operation(summary = "회원가입용 학교 학적정보 삭제",
            description = "세션에 임시 보관된 학교 학적정보를 삭제합니다.")
    ResponseEntity<Void> clear(@Parameter(hidden = true) HttpServletRequest request) {
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

    @GetMapping("/api/users/me/school-verification/department")
    @ResponseBody
    @Operation(summary = "학교·회원 학과 불일치 조회",
            description = "현재 회원 학과와 학교 학적정보의 학과를 조회합니다.",
            security = @SecurityRequirement(name = "bearerAuth"))
    ResponseEntity<DepartmentMismatchResponse> departmentMismatch(
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authorization,
            @Parameter(hidden = true) HttpServletRequest request,
            @Parameter(hidden = true) HttpServletResponse response) {
        noStore(response);
        AuthorizedResult<MeResponse> authenticated = users.getMe(BearerTokens.require(authorization),
                cookies.readRefreshToken(request));
        SchoolAcademicProfile profile = sessions.requireAccountProfile(request, authenticated.body().userUuid());
        ResponseEntity.BodyBuilder builder = ResponseEntity.ok().cacheControl(CacheControl.noStore());
        if (authenticated.newAccessToken() != null)
            builder.header(UserController.REFRESHED_ACCESS_TOKEN, authenticated.newAccessToken());
        if (authenticated.newRefreshToken() != null)
            builder.header(TokenCookieManager.SET_COOKIE, cookies.create(authenticated.newRefreshToken()));
        return builder.body(new DepartmentMismatchResponse(authenticated.body().department(), profile.department()));
    }

    @PostMapping("/api/users/me/school-verification/department/confirm")
    @ResponseBody
    @Operation(summary = "학교 학과로 변경 후 학생 인증",
            description = "회원 학과를 학교 학적정보로 변경하고 학생 인증을 완료합니다.",
            security = @SecurityRequirement(name = "bearerAuth"))
    ResponseEntity<MeResponse> confirmDepartment(
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authorization,
            @Parameter(hidden = true) HttpServletRequest request,
            @Parameter(hidden = true) HttpServletResponse response) {
        noStore(response);
        String access = BearerTokens.require(authorization);
        String refresh = cookies.readRefreshToken(request);
        AuthorizedResult<MeResponse> authenticated = users.getMe(access, refresh);
        SchoolAcademicProfile profile = sessions.requireAccountProfile(request, authenticated.body().userUuid());
        String effectiveAccess = authenticated.newAccessToken() == null ? access : authenticated.newAccessToken();
        String effectiveRefresh = authenticated.newRefreshToken() == null ? refresh : authenticated.newRefreshToken();
        AuthorizedResult<MeResponse> updated = users.updateProfile(effectiveAccess, effectiveRefresh,
                new ProfileUpdateRequest(null, profile.department(), null, null));
        var festivalProfile = festivalUsers.verifySchool(updated.body().userUuid(), profile);
        sessions.clearProfile(request);
        AuthorizedResult<MeResponse> result = new AuthorizedResult<>(
                updated.body().withFestivalProfile(festivalProfile),
                updated.newAccessToken() == null ? authenticated.newAccessToken() : updated.newAccessToken(),
                updated.newRefreshToken() == null ? authenticated.newRefreshToken() : updated.newRefreshToken());
        ResponseEntity.BodyBuilder builder = ResponseEntity.ok().cacheControl(CacheControl.noStore());
        if (result.newAccessToken() != null) builder.header(UserController.REFRESHED_ACCESS_TOKEN, result.newAccessToken());
        if (result.newRefreshToken() != null) builder.header(TokenCookieManager.SET_COOKIE,
                cookies.create(result.newRefreshToken()));
        return builder.body(result.body());
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
    public record DepartmentMismatchResponse(String currentDepartment, String schoolDepartment) { }

    private boolean sameName(String current, String school) {
        return normalizeText(current).equals(normalizeText(school));
    }

    private boolean sameStudentNo(String current, String school) {
        return value(current).replaceAll("[\\s-]", "").equals(value(school).replaceAll("[\\s-]", ""));
    }

    private boolean sameDepartment(String current, String school) {
        return normalizeText(current).equals(normalizeText(school));
    }

    private String normalizeText(String value) { return value(value).trim(); }
    private String value(String value) { return value == null ? "" : value; }
}
