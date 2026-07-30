package org.syu_likelion.Feata_2026.user;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.syu_likelion.Feata_2026.auth.AuthorizedSsoExecutor.AuthorizedResult;
import org.syu_likelion.Feata_2026.auth.AuthDtos.MessageResponse;
import org.syu_likelion.Feata_2026.auth.BearerTokens;
import org.syu_likelion.Feata_2026.auth.TokenCookieManager;
import org.syu_likelion.Feata_2026.user.UserDtos.EmailCodeRequest;
import org.syu_likelion.Feata_2026.user.UserDtos.EmailRequest;
import org.syu_likelion.Feata_2026.user.UserDtos.MeResponse;
import org.syu_likelion.Feata_2026.user.UserDtos.PasswordChangeRequest;
import org.syu_likelion.Feata_2026.user.UserDtos.ProfileUpdateRequest;

@RestController
@RequestMapping("/api/users/me")
@Tag(name = "User", description = "내 정보와 계정 관리")
@SecurityRequirement(name = "bearerAuth")
public class UserController {
    public static final String REFRESHED_ACCESS_TOKEN = "X-Access-Token";
    private final UserService userService;
    private final TokenCookieManager cookies;

    public UserController(UserService userService, TokenCookieManager cookies) {
        this.userService = userService;
        this.cookies = cookies;
    }

    @GetMapping
    ResponseEntity<MeResponse> getMe(@RequestHeader(value = "Authorization", required = false) String authorization,
                                     HttpServletRequest request) {
        return response(userService.getMe(BearerTokens.require(authorization), cookies.readRefreshToken(request)));
    }

    @PatchMapping("/profile")
    ResponseEntity<MeResponse> updateProfile(@RequestHeader(value = "Authorization", required = false) String authorization,
                                             HttpServletRequest servletRequest,
                                             @Valid @RequestBody ProfileUpdateRequest request) {
        return response(userService.updateProfile(BearerTokens.require(authorization),
                cookies.readRefreshToken(servletRequest), request));
    }

    @PostMapping("/email/verification")
    ResponseEntity<MessageResponse> sendEmailCode(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                  HttpServletRequest servletRequest,
                                                  @Valid @RequestBody EmailRequest request) {
        AuthorizedResult<Void> result = userService.sendNewEmailCode(BearerTokens.require(authorization),
                cookies.readRefreshToken(servletRequest), request);
        return message(result, "새 이메일로 인증번호를 발송했습니다.");
    }

    @PostMapping("/email/verification/confirm")
    ResponseEntity<MessageResponse> verifyEmailCode(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                    HttpServletRequest servletRequest,
                                                    @Valid @RequestBody EmailCodeRequest request) {
        AuthorizedResult<Void> result = userService.verifyNewEmailCode(BearerTokens.require(authorization),
                cookies.readRefreshToken(servletRequest), request);
        return message(result, "새 이메일 인증이 완료되었습니다.");
    }

    @PatchMapping("/email")
    ResponseEntity<MessageResponse> changeEmail(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                HttpServletRequest servletRequest,
                                                @Valid @RequestBody EmailRequest request) {
        AuthorizedResult<Void> result = userService.changeEmail(BearerTokens.require(authorization),
                cookies.readRefreshToken(servletRequest), request);
        return message(result, "이메일이 변경되었습니다.");
    }

    @PatchMapping("/password")
    ResponseEntity<Void> changePassword(@RequestHeader(value = "Authorization", required = false) String authorization,
                                        HttpServletRequest servletRequest,
                                        @Valid @RequestBody PasswordChangeRequest request) {
        userService.changePassword(BearerTokens.require(authorization), cookies.readRefreshToken(servletRequest), request);
        return ResponseEntity.noContent().header(TokenCookieManager.SET_COOKIE, cookies.clear()).build();
    }

    @DeleteMapping
    ResponseEntity<Void> withdraw(@RequestHeader(value = "Authorization", required = false) String authorization,
                                  HttpServletRequest servletRequest) {
        userService.withdraw(BearerTokens.require(authorization), cookies.readRefreshToken(servletRequest));
        return ResponseEntity.noContent().header(TokenCookieManager.SET_COOKIE, cookies.clear()).build();
    }

    private <T> ResponseEntity<T> response(AuthorizedResult<T> result) {
        return headers(result).body(result.body());
    }

    private ResponseEntity<MessageResponse> message(AuthorizedResult<Void> result, String message) {
        return headers(result).body(new MessageResponse(message));
    }

    private ResponseEntity.BodyBuilder headers(AuthorizedResult<?> result) {
        ResponseEntity.BodyBuilder response = ResponseEntity.ok();
        if (result.newAccessToken() != null) response.header(REFRESHED_ACCESS_TOKEN, result.newAccessToken());
        if (result.newRefreshToken() != null) response.header(TokenCookieManager.SET_COOKIE, cookies.create(result.newRefreshToken()));
        return response;
    }
}
