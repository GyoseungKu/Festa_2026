package org.syu_likelion.Festa_2026.user;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.syu_likelion.Festa_2026.auth.AuthorizedSsoExecutor.AuthorizedResult;
import org.syu_likelion.Festa_2026.auth.AuthDtos.MessageResponse;
import org.syu_likelion.Festa_2026.auth.BearerTokens;
import org.syu_likelion.Festa_2026.auth.TokenCookieManager;
import org.syu_likelion.Festa_2026.user.UserDtos.EmailCodeRequest;
import org.syu_likelion.Festa_2026.user.UserDtos.EmailRequest;
import org.syu_likelion.Festa_2026.user.UserDtos.MeResponse;
import org.syu_likelion.Festa_2026.user.UserDtos.PasswordChangeRequest;
import org.syu_likelion.Festa_2026.user.UserDtos.ProfileUpdateRequest;

@RestController
@RequestMapping("/api/users/me")
@Tag(name = "User", description = "내 정보와 계정 관리")
@SecurityRequirement(name = "bearerAuth")
public class UserController {
    public static final String REFRESHED_ACCESS_TOKEN = "X-Access-Token";
    private final UserService userService;
    private final TokenCookieManager cookies;
    private final FestivalWithdrawalService withdrawal;

    public UserController(UserService userService, TokenCookieManager cookies, FestivalWithdrawalService withdrawal) {
        this.userService = userService;
        this.cookies = cookies;
        this.withdrawal = withdrawal;
    }

    @GetMapping
    @Operation(summary = "내 정보 조회",
            description = "SSO에서 현재 사용자 정보를 조회하고 축제 홈페이지 역할을 함께 반환합니다. 프로필과 학적 필드는 null일 수 있습니다.")
    ResponseEntity<MeResponse> getMe(
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authorization,
            @Parameter(hidden = true) HttpServletRequest request) {
        return response(userService.getMe(BearerTokens.require(authorization), cookies.readRefreshToken(request)));
    }

    @PatchMapping("/profile")
    @Operation(summary = "기본 프로필 수정",
            description = "SSO의 전화번호·학과·학년·재학 상태 중 전달한 필드를 수정하고 갱신된 내 정보를 반환합니다. 이름·학번·생년월일은 변경할 수 없습니다. 학과를 전달하면 기존 학생 인증이 취소됩니다.")
    ResponseEntity<MeResponse> updateProfile(
                                             @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authorization,
                                             @Parameter(hidden = true) HttpServletRequest servletRequest,
                                             @Valid @RequestBody ProfileUpdateRequest request) {
        return response(userService.updateProfile(BearerTokens.require(authorization),
                cookies.readRefreshToken(servletRequest), request));
    }

    @PostMapping("/email/verification")
    @Operation(summary = "새 이메일 인증번호 발송",
            description = "변경할 새 이메일 주소로 SSO 인증번호를 발송합니다.")
    ResponseEntity<MessageResponse> sendEmailCode(
                                                  @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authorization,
                                                  @Parameter(hidden = true) HttpServletRequest servletRequest,
                                                  @Valid @RequestBody EmailRequest request) {
        AuthorizedResult<Void> result = userService.sendNewEmailCode(BearerTokens.require(authorization),
                cookies.readRefreshToken(servletRequest), request);
        return message(result, "새 이메일로 인증번호를 발송했습니다.");
    }

    @PostMapping("/email/verification/confirm")
    @Operation(summary = "새 이메일 인증번호 확인",
            description = "새 이메일 주소와 인증번호를 SSO에서 검증합니다.")
    ResponseEntity<MessageResponse> verifyEmailCode(
                                                    @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authorization,
                                                    @Parameter(hidden = true) HttpServletRequest servletRequest,
                                                    @Valid @RequestBody EmailCodeRequest request) {
        AuthorizedResult<Void> result = userService.verifyNewEmailCode(BearerTokens.require(authorization),
                cookies.readRefreshToken(servletRequest), request);
        return message(result, "새 이메일 인증이 완료되었습니다.");
    }

    @PatchMapping("/email")
    @Operation(summary = "이메일 변경 적용",
            description = "인증이 완료된 새 이메일 주소로 SSO 계정 이메일을 변경합니다.")
    ResponseEntity<MessageResponse> changeEmail(
                                                @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authorization,
                                                @Parameter(hidden = true) HttpServletRequest servletRequest,
                                                @Valid @RequestBody EmailRequest request) {
        AuthorizedResult<Void> result = userService.changeEmail(BearerTokens.require(authorization),
                cookies.readRefreshToken(servletRequest), request);
        return message(result, "이메일이 변경되었습니다.");
    }

    @PatchMapping("/password")
    @ApiResponse(responseCode = "204", description = "처리 완료, 응답 본문 없음", content = @Content)
    @Operation(summary = "비밀번호 변경",
            description = "로그인 사용자의 Bearer Access Token과 현재 비밀번호를 확인한 뒤 SSO 비밀번호를 변경합니다. 성공하면 204를 반환하고 Festa Refresh Token 쿠키를 삭제합니다. 프런트는 메모리의 Access Token을 즉시 버리고 로그인 화면으로 이동해야 합니다.")
    ResponseEntity<Void> changePassword(
                                        @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authorization,
                                        @Parameter(hidden = true) HttpServletRequest servletRequest,
                                        @Valid @RequestBody PasswordChangeRequest request) {
        userService.changePassword(BearerTokens.require(authorization), cookies.readRefreshToken(servletRequest), request);
        return ResponseEntity.noContent().header(TokenCookieManager.SET_COOKIE, cookies.clear()).build();
    }

    @DeleteMapping("/festival")
    @ApiResponse(responseCode = "204", description = "처리 완료, 응답 본문 없음", content = @Content)
    @Operation(summary = "축제 서비스 이용 정보 삭제",
            description = "SSO 계정은 유지하고 본인의 축제 사용자 정보와 개인 연결 데이터를 삭제합니다. 게시글과 투표 기록은 작성자를 알 수 없음으로 익명화하여 보존합니다. 성공 후 Access Token을 버리고 로그인 화면으로 이동하세요. 다시 로그인하면 신규 사용자로 연결됩니다.")
    ResponseEntity<Void> withdrawFestival(
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authorization,
            @Parameter(hidden = true) HttpServletRequest request) {
        withdrawal.withdraw(userService.authenticateForWithdrawal(BearerTokens.require(authorization),
                cookies.readRefreshToken(request)));
        if (request.getSession(false) != null) request.getSession(false).invalidate();
        return ResponseEntity.noContent().header(TokenCookieManager.SET_COOKIE, cookies.clear()).build();
    }

    @DeleteMapping
    @ApiResponse(responseCode = "204", description = "처리 완료, 응답 본문 없음", content = @Content)
    @Operation(summary = "SSO 계정 탈퇴",
            description = "SSO 계정 자체를 탈퇴 처리하고 Refresh Token 쿠키를 삭제합니다. 축제 사이트만 탈퇴하는 API가 아닙니다.")
    ResponseEntity<Void> withdraw(
                                  @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authorization,
                                  @Parameter(hidden = true) HttpServletRequest servletRequest) {
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
