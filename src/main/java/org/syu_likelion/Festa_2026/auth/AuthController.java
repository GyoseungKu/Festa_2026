package org.syu_likelion.Festa_2026.auth;

import io.swagger.v3.oas.annotations.Operation;
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

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Auth", description = "회원가입, 로그인과 토큰 관리")
public class AuthController {
    private final AuthService authService;
    private final TokenRefreshCoordinator refreshCoordinator;
    private final TokenCookieManager cookies;

    public AuthController(AuthService authService, TokenRefreshCoordinator refreshCoordinator,
                          TokenCookieManager cookies) {
        this.authService = authService;
        this.refreshCoordinator = refreshCoordinator;
        this.cookies = cookies;
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
            description = "SSO 계정을 생성합니다. 아이디, 이메일, 비밀번호, 이름, 학과, 학번은 필수이고 전화번호는 선택입니다. 학년, 재학 상태, 생년월일은 nullable입니다.")
    SignupResponse signup(@Valid @RequestBody SignupRequest request) {
        return authService.signup(request);
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
