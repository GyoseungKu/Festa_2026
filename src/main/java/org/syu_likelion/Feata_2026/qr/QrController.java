package org.syu_likelion.Feata_2026.qr;

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
import org.syu_likelion.Feata_2026.auth.AuthorizedSsoExecutor.AuthorizedResult;
import org.syu_likelion.Feata_2026.auth.BearerTokens;
import org.syu_likelion.Feata_2026.auth.TokenCookieManager;
import org.syu_likelion.Feata_2026.qr.QrDtos.QrScanRequest;
import org.syu_likelion.Feata_2026.qr.QrDtos.QrTokenResponse;
import org.syu_likelion.Feata_2026.qr.QrDtos.QrUserView;
import org.syu_likelion.Feata_2026.user.UserController;

@RestController
@RequestMapping("/api/qr")
@Tag(name = "QR", description = "동적 사용자 QR 발급과 관리자 스캔")
@SecurityRequirement(name = "bearerAuth")
public class QrController {
    private final QrService qrService;
    private final TokenCookieManager cookies;

    public QrController(QrService qrService, TokenCookieManager cookies) {
        this.qrService = qrService;
        this.cookies = cookies;
    }

    @PostMapping("/tokens")
    @Operation(summary = "내 QR 토큰 발급",
            description = "현재 로그인 사용자와 연결된 256비트 임시 난수 토큰을 발급합니다. 기본 유효시간은 60초이며 기존 토큰은 원래 만료 시각까지 유지됩니다.")
    ResponseEntity<QrTokenResponse> issue(
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authorization,
            @Parameter(hidden = true) HttpServletRequest request) {
        AuthorizedResult<QrTokenResponse> result = qrService.issue(
                BearerTokens.require(authorization), cookies.readRefreshToken(request));
        return response(result);
    }

    @PostMapping("/scan")
    @Operation(summary = "QR 토큰으로 사용자 조회",
            description = "관리자의 축제 역할을 확인한 후 QR 사용자의 정보를 권한별로 제한·마스킹하여 반환합니다. USER 역할은 사용할 수 없습니다.")
    ResponseEntity<QrUserView> scan(
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authorization,
            @Parameter(hidden = true) HttpServletRequest request,
            @Valid @RequestBody QrScanRequest scanRequest) {
        AuthorizedResult<QrUserView> result = qrService.scan(BearerTokens.require(authorization),
                cookies.readRefreshToken(request), scanRequest.token());
        return response(result);
    }

    private <T> ResponseEntity<T> response(AuthorizedResult<T> result) {
        ResponseEntity.BodyBuilder response = ResponseEntity.ok();
        if (result.newAccessToken() != null) {
            response.header(UserController.REFRESHED_ACCESS_TOKEN, result.newAccessToken());
        }
        if (result.newRefreshToken() != null) {
            response.header(TokenCookieManager.SET_COOKIE, cookies.create(result.newRefreshToken()));
        }
        return response.body(result.body());
    }
}
