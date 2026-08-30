package org.syu_likelion.Festa_2026.user;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.syu_likelion.Festa_2026.admin.AdminAccessService;
import org.syu_likelion.Festa_2026.admin.AdminAccessService.AdminIdentity;
import org.syu_likelion.Festa_2026.auth.AuthorizedSsoExecutor.AuthorizedResult;
import org.syu_likelion.Festa_2026.auth.BearerTokens;
import org.syu_likelion.Festa_2026.auth.TokenCookieManager;
import org.syu_likelion.Festa_2026.error.ApiException;
import org.syu_likelion.Festa_2026.user.SchoolVerificationApprovalService.RequestResponse;

@RestController
@RequestMapping("/api/admin/school-verifications")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "School Verification Admin", description = "SUPER_ADMIN 학생 인증 승인 관리")
public class SchoolVerificationAdminController {
    private final AdminAccessService access;
    private final SchoolVerificationApprovalService approvals;
    private final TokenCookieManager cookies;

    public SchoolVerificationAdminController(AdminAccessService access,
                                             SchoolVerificationApprovalService approvals,
                                             TokenCookieManager cookies) {
        this.access = access;
        this.approvals = approvals;
        this.cookies = cookies;
    }

    @GetMapping
    @Operation(summary = "학생 인증 미승인 요청 목록",
            description = "SUPER_ADMIN이 회원정보와 학교 학적정보가 다른 요청을 조회합니다.")
    ResponseEntity<List<RequestResponse>> list(
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authorization,
            @Parameter(hidden = true) HttpServletRequest request) {
        AuthorizedResult<AdminIdentity> admin = requireSuperAdmin(authorization, request);
        return response(admin, approvals.list());
    }

    @PostMapping("/{id}/approve")
    @Operation(summary = "학생 인증 요청 승인",
            description = "SUPER_ADMIN이 미승인 요청을 승인하고 학생 인증을 완료합니다.")
    ResponseEntity<Void> approve(
            @PathVariable Long id,
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authorization,
            @Parameter(hidden = true) HttpServletRequest request) {
        AuthorizedResult<AdminIdentity> admin = requireSuperAdmin(authorization, request);
        approvals.approve(id);
        return response(admin, null);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "학생 인증 요청 삭제",
            description = "SUPER_ADMIN이 미승인 요청을 삭제합니다. 사용자 계정은 삭제하지 않습니다.")
    ResponseEntity<Void> delete(
            @PathVariable Long id,
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authorization,
            @Parameter(hidden = true) HttpServletRequest request) {
        AuthorizedResult<AdminIdentity> admin = requireSuperAdmin(authorization, request);
        approvals.delete(id);
        return response(admin, null);
    }

    private AuthorizedResult<AdminIdentity> requireSuperAdmin(String authorization, HttpServletRequest request) {
        AuthorizedResult<AdminIdentity> admin = access.authenticate(BearerTokens.require(authorization),
                cookies.readRefreshToken(request));
        if (!admin.body().hasRole(FestivalRole.SUPER_ADMIN)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "SUPER_ADMIN_REQUIRED",
                    "학생 인증 승인 요청은 SUPER_ADMIN만 처리할 수 있습니다.");
        }
        return admin;
    }

    private <T> ResponseEntity<T> response(AuthorizedResult<?> authenticated, T body) {
        ResponseEntity.BodyBuilder builder = ResponseEntity.ok();
        if (authenticated.newAccessToken() != null)
            builder.header(UserController.REFRESHED_ACCESS_TOKEN, authenticated.newAccessToken());
        if (authenticated.newRefreshToken() != null)
            builder.header(TokenCookieManager.SET_COOKIE, cookies.create(authenticated.newRefreshToken()));
        return builder.body(body);
    }
}
