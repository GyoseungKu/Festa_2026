package org.syu_likelion.Festa_2026.auth;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Email;
import org.syu_likelion.Festa_2026.auth.AuthDtos.AvailabilityResponse;
import org.syu_likelion.Festa_2026.auth.AuthDtos.EmailCodeRequest;
import org.syu_likelion.Festa_2026.auth.AuthDtos.EmailRequest;
import org.syu_likelion.Festa_2026.auth.AuthDtos.LoginRequest;
import org.syu_likelion.Festa_2026.auth.AuthDtos.MessageResponse;
import org.syu_likelion.Festa_2026.auth.AuthDtos.SignupRequest;
import org.syu_likelion.Festa_2026.auth.AuthDtos.SignupResponse;
import org.syu_likelion.Festa_2026.auth.AuthDtos.TokenResponse;
import org.syu_likelion.Festa_2026.auth.AuthDtos.RecoveryEmailSendRequest;
import org.syu_likelion.Festa_2026.auth.AuthDtos.FindIdVerifyRequest;
import org.syu_likelion.Festa_2026.auth.AuthDtos.FindIdResponse;
import org.syu_likelion.Festa_2026.auth.AuthDtos.ResetPasswordRequest;
import org.syu_likelion.Festa_2026.sso.SsoResult;
import org.syu_likelion.Festa_2026.schoolsso.SchoolAcademicProfile;
import org.syu_likelion.Festa_2026.schoolsso.SchoolSsoSessionStore;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Auth", description = "회원가입, 로그인과 토큰 관리")
@Validated
public class AuthController {
    private final AuthService authService;
    private final TokenRefreshCoordinator refreshCoordinator;
    private final TokenCookieManager cookies;
    private final SchoolSsoSessionStore schoolSsoSessions;

    public AuthController(AuthService authService, TokenRefreshCoordinator refreshCoordinator,
                          TokenCookieManager cookies, SchoolSsoSessionStore schoolSsoSessions) {
        this.authService = authService;
        this.refreshCoordinator = refreshCoordinator;
        this.cookies = cookies;
        this.schoolSsoSessions = schoolSsoSessions;
    }

    @GetMapping("/check/login-id")
    @Operation(summary = "로그인 아이디 중복 확인",
            description = "로그인 없이 loginId의 사용 가능 여부를 SSO에서 확인합니다. available=true이면 사용할 수 있습니다.")
    AvailabilityResponse checkLoginId(
            @RequestParam @NotBlank @Size(min = 4, max = 50, message = "아이디는 4~50자여야 합니다.") String loginId) {
        return authService.checkLoginId(loginId);
    }

    @GetMapping("/check/email")
    @Operation(summary = "이메일 중복 확인",
            description = "로그인 없이 email의 사용 가능 여부를 SSO에서 확인합니다. 이메일 인증 완료 여부를 확인하는 API는 아닙니다.")
    AvailabilityResponse checkEmail(
            @RequestParam @NotBlank @Email @Size(max = 254) String email) {
        return authService.checkEmail(email);
    }

    @GetMapping("/check/student-no")
    @Operation(summary = "학번 중복 확인",
            description = "로그인 없이 studentNo의 중복 여부를 SSO에서 확인합니다. 학교 학생 인증과는 별개입니다.")
    AvailabilityResponse checkStudentNo(
            @RequestParam @NotBlank @Size(max = 50) String studentNo) {
        return authService.checkStudentNo(studentNo);
    }

    @GetMapping("/check/phone")
    @Operation(summary = "전화번호 중복 확인",
            description = "로그인 없이 phone의 중복 여부를 SSO에서 확인합니다.")
    AvailabilityResponse checkPhone(
            @RequestParam @NotBlank
            @Pattern(regexp = "^[0-9]{10,11}$", message = "전화번호는 숫자 10~11자리여야 합니다.")
            String phone) {
        return authService.checkPhone(phone);
    }

    @PostMapping("/signup/email/send")
    @Operation(summary = "회원가입 이메일 인증번호 발송",
            description = "회원가입에 사용할 이메일로 SSO 인증번호를 발송합니다.")
    MessageResponse sendSignupEmail(@Valid @RequestBody EmailRequest request) {
        authService.sendSignupEmailCode(request);
        return new MessageResponse("인증번호를 발송했습니다.");
    }

    @PostMapping("/signup/email/verify")
    @Operation(summary = "회원가입 이메일 인증번호 확인",
            description = "이메일과 인증번호를 SSO에서 검증합니다. 검증 완료 후 회원가입을 진행할 수 있습니다.")
    MessageResponse verifySignupEmail(@Valid @RequestBody EmailCodeRequest request) {
        authService.verifySignupEmailCode(request);
        return new MessageResponse("이메일 인증이 완료되었습니다.");
    }

    @PostMapping("/email/send")
    @Operation(summary = "계정 복구 이메일 인증번호 발송",
            description = "로그인 전 아이디 찾기(FIND_ID) 또는 비밀번호 재설정(RESET_PASSWORD) 인증번호를 발송합니다. RESET_PASSWORD에는 loginId가 필요합니다. 계정 존재 여부는 응답으로 노출하지 않습니다.")
    MessageResponse sendRecoveryEmail(@Valid @RequestBody RecoveryEmailSendRequest request) {
        authService.sendRecoveryEmailCode(request);
        return new MessageResponse("입력한 정보와 일치하는 계정이 있다면 인증번호를 발송했습니다.");
    }

    @PostMapping("/email/find-id/verify")
    @Operation(summary = "아이디 찾기 인증번호 확인",
            description = "이메일 인증번호를 확인하고 일치하는 로그인 아이디를 반환합니다.")
    FindIdResponse findId(@Valid @RequestBody FindIdVerifyRequest request) {
        return authService.findId(request);
    }

    @PostMapping("/email/reset-password/verify")
    @Operation(summary = "로그인 전 비밀번호 재설정",
            description = "로그인 아이디, 이메일, 인증번호와 새 비밀번호를 검증하고 SSO 비밀번호를 즉시 변경합니다. 별도의 resetToken은 사용하지 않습니다.")
    ResponseEntity<MessageResponse> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.ok()
                .header(TokenCookieManager.SET_COOKIE, cookies.clear())
                .body(new MessageResponse("비밀번호가 재설정되었습니다. 새 비밀번호로 로그인해 주세요."));
    }

    @PostMapping("/signup")
    @Operation(summary = "회원가입",
            description = "SSO 계정을 생성합니다. academicInfoSource가 MANUAL이면 입력한 학적정보를, SCHOOL_SSO이면 학교 SSO에서 검증해 세션에 보관한 이름·학번·학과를 사용합니다.")
    SignupResponse signup(@Valid @RequestBody SignupRequest request, HttpServletRequest servletRequest) {
        SignupRequest effective = request;
        SchoolAcademicProfile verifiedProfile = null;
        if (request.effectiveAcademicInfoSource() == AuthDtos.AcademicInfoSource.SCHOOL_SSO) {
            verifiedProfile = schoolSsoSessions.requireProfile(servletRequest);
            effective = request.withVerifiedAcademicInfo(verifiedProfile.name(), verifiedProfile.studentNo(),
                    verifiedProfile.department());
        }
        SignupResponse response = authService.signup(effective, verifiedProfile);
        if (request.effectiveAcademicInfoSource() == AuthDtos.AcademicInfoSource.SCHOOL_SSO) {
            schoolSsoSessions.clearProfile(servletRequest);
        }
        return response;
    }

    @PostMapping("/login")
    @Operation(summary = "로그인",
            description = "SSO 로그인 후 Access Token을 응답 본문으로 반환하고 Refresh Token은 HttpOnly 쿠키로 설정합니다.")
    ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthService.LoginResult result = authService.login(request);
        return ResponseEntity.ok()
                .header(TokenCookieManager.SET_COOKIE, cookies.create(result.refreshToken()))
                .body(result.tokens());
    }

    @PostMapping("/token/refresh")
    @Operation(summary = "Access Token 갱신",
            description = "HttpOnly Refresh Token 쿠키를 사용해 새 Access Token을 발급합니다. Swagger UI에서는 로그인 후 쿠키가 자동으로 사용됩니다.")
    ResponseEntity<TokenResponse> refresh(@Parameter(hidden = true) HttpServletRequest request) {
        String currentRefreshToken = cookies.readRefreshToken(request);
        SsoResult<TokenResponse> result = refreshCoordinator.refresh(currentRefreshToken);
        ResponseEntity.BodyBuilder response = ResponseEntity.ok();
        if (result.refreshToken() != null && !result.refreshToken().isBlank()) {
            response.header(TokenCookieManager.SET_COOKIE, cookies.create(result.refreshToken()));
        }
        return response.body(result.body());
    }

    @PostMapping("/logout")
    @ApiResponse(responseCode = "204", description = "처리 완료, 응답 본문 없음", content = @Content)
    @Operation(summary = "로그아웃",
            description = "SSO 토큰을 폐기하고 축제 홈페이지의 Refresh Token 쿠키를 삭제합니다.",
            security = @SecurityRequirement(name = "bearerAuth"))
    ResponseEntity<Void> logout(
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authorization) {
        if (authorization != null && authorization.startsWith("Bearer ") && authorization.length() > 7) {
            authService.logout(BearerTokens.require(authorization));
        }
        return ResponseEntity.noContent()
                .header(TokenCookieManager.SET_COOKIE, cookies.clear())
                .build();
    }
}
