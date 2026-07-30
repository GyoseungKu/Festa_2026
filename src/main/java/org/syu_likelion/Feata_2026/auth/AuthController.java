package org.syu_likelion.Feata_2026.auth;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.syu_likelion.Feata_2026.auth.AuthDtos.EmailCodeRequest;
import org.syu_likelion.Feata_2026.auth.AuthDtos.EmailRequest;
import org.syu_likelion.Feata_2026.auth.AuthDtos.LoginRequest;
import org.syu_likelion.Feata_2026.auth.AuthDtos.MessageResponse;
import org.syu_likelion.Feata_2026.auth.AuthDtos.SignupRequest;
import org.syu_likelion.Feata_2026.auth.AuthDtos.SignupResponse;
import org.syu_likelion.Feata_2026.auth.AuthDtos.TokenResponse;
import org.syu_likelion.Feata_2026.sso.SsoResult;

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
    MessageResponse sendSignupEmail(@Valid @RequestBody EmailRequest request) {
        authService.sendSignupEmailCode(request);
        return new MessageResponse("인증번호를 발송했습니다.");
    }

    @PostMapping("/signup/email/verify")
    MessageResponse verifySignupEmail(@Valid @RequestBody EmailCodeRequest request) {
        authService.verifySignupEmailCode(request);
        return new MessageResponse("이메일 인증이 완료되었습니다.");
    }

    @PostMapping("/signup")
    SignupResponse signup(@Valid @RequestBody SignupRequest request) {
        return authService.signup(request);
    }

    @PostMapping("/login")
    ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthService.LoginResult result = authService.login(request);
        return ResponseEntity.ok()
                .header(TokenCookieManager.SET_COOKIE, cookies.create(result.refreshToken()))
                .body(result.tokens());
    }

    @PostMapping("/token/refresh")
    ResponseEntity<TokenResponse> refresh(HttpServletRequest request) {
        String currentRefreshToken = cookies.readRefreshToken(request);
        SsoResult<TokenResponse> result = refreshCoordinator.refresh(currentRefreshToken);
        ResponseEntity.BodyBuilder response = ResponseEntity.ok();
        if (result.refreshToken() != null && !result.refreshToken().isBlank()) {
            response.header(TokenCookieManager.SET_COOKIE, cookies.create(result.refreshToken()));
        }
        return response.body(result.body());
    }

    @PostMapping("/logout")
    @Operation(security = @SecurityRequirement(name = "bearerAuth"))
    ResponseEntity<Void> logout(@RequestHeader(value = "Authorization", required = false) String authorization) {
        if (authorization != null && authorization.startsWith("Bearer ") && authorization.length() > 7) {
            authService.logout(BearerTokens.require(authorization));
        }
        return ResponseEntity.noContent()
                .header(TokenCookieManager.SET_COOKIE, cookies.clear())
                .build();
    }
}
