package org.syu_likelion.Festa_2026.qr;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.syu_likelion.Festa_2026.auth.AuthorizedSsoExecutor.AuthorizedResult;
import org.syu_likelion.Festa_2026.auth.BearerTokens;
import org.syu_likelion.Festa_2026.auth.TokenCookieManager;
import org.syu_likelion.Festa_2026.qr.QrDtos.QrScanRequest;
import org.syu_likelion.Festa_2026.qr.QrDtos.QrTokenResponse;
import org.syu_likelion.Festa_2026.qr.QrDtos.QrUserView;
import org.syu_likelion.Festa_2026.qr.QrDtos.UserSearchRequest;
import org.syu_likelion.Festa_2026.qr.QrDtos.UserSearchResponse;
import org.syu_likelion.Festa_2026.qr.QrDtos.UserRoleUpdateRequest;
import org.syu_likelion.Festa_2026.qr.QrDtos.UserRoleUpdateResponse;
import org.syu_likelion.Festa_2026.user.UserController;

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

    @PostMapping("/search")
    @Operation(summary = "사용자 정보 검색",
            description = "STAFF 이상이 원본 사용자 정보로 검색합니다. 검색 결과 필드는 조회 권한에 따라 마스킹·제한됩니다.")
    ResponseEntity<UserSearchResponse> search(
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authorization,
            @Parameter(hidden = true) HttpServletRequest request,
            @Valid @RequestBody UserSearchRequest searchRequest) {
        return response(qrService.search(BearerTokens.require(authorization), cookies.readRefreshToken(request),
                searchRequest.query()));
    }

    @PatchMapping("/users/{userUuid}/role")
    @Operation(summary = "사용자 관리 권한 변경",
            description = "ADMIN은 USER·STAFF 범위만, SUPER_ADMIN은 전체 관리 권한을 변경할 수 있습니다. BOOTH_MANAGER는 부스 담당자 지정에서 관리합니다.")
    ResponseEntity<UserRoleUpdateResponse> updateRole(@PathVariable java.util.UUID userUuid,
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authorization,
            @Parameter(hidden = true) HttpServletRequest request,
            @Valid @RequestBody UserRoleUpdateRequest body) {
        return response(qrService.updateRole(BearerTokens.require(authorization), cookies.readRefreshToken(request),
                userUuid, body.managementRole()));
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
