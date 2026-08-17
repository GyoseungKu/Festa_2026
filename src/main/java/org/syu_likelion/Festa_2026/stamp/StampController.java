package org.syu_likelion.Festa_2026.stamp;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.syu_likelion.Festa_2026.auth.AuthorizedSsoExecutor.AuthorizedResult;
import org.syu_likelion.Festa_2026.auth.BearerTokens;
import org.syu_likelion.Festa_2026.auth.TokenCookieManager;
import org.syu_likelion.Festa_2026.stamp.StampDtos.BoothStampAdminResponse;
import org.syu_likelion.Festa_2026.stamp.StampDtos.QrStampRequest;
import org.syu_likelion.Festa_2026.stamp.StampDtos.StampTargetResponse;
import org.syu_likelion.Festa_2026.user.UserController;

@RestController
@RequestMapping("/api/booths/{boothId}/stamps")
@Tag(name = "Stamp", description = "부스 스탬프 QR 지급·회수와 관리자 이력")
@SecurityRequirement(name = "bearerAuth")
public class StampController {
    private final StampService stamps;
    private final TokenCookieManager cookies;
    public StampController(StampService stamps, TokenCookieManager cookies) { this.stamps = stamps; this.cookies = cookies; }

    @PostMapping("/qr/lookup")
    @Operation(summary = "QR 사용자와 현재 스탬프 상태 조회")
    ResponseEntity<StampTargetResponse> lookup(@PathVariable Long boothId, @Valid @RequestBody QrStampRequest body,
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authorization,
            @Parameter(hidden = true) HttpServletRequest request) {
        return ok(stamps.lookupQr(boothId, body.token(), BearerTokens.require(authorization), cookies.readRefreshToken(request)));
    }
    @PostMapping("/qr/grant")
    @Operation(summary = "QR로 스탬프 지급", description = "BOOTH_MANAGER는 담당 부스에서만 사용할 수 있습니다.")
    ResponseEntity<StampTargetResponse> grantQr(@PathVariable Long boothId, @Valid @RequestBody QrStampRequest body,
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authorization,
            @Parameter(hidden = true) HttpServletRequest request) {
        return ok(stamps.grantQr(boothId, body.token(), BearerTokens.require(authorization), cookies.readRefreshToken(request)));
    }
    @PostMapping("/qr/revoke")
    @Operation(summary = "QR로 스탬프 회수", description = "BOOTH_MANAGER는 담당 부스에서만 사용할 수 있습니다.")
    ResponseEntity<StampTargetResponse> revokeQr(@PathVariable Long boothId, @Valid @RequestBody QrStampRequest body,
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authorization,
            @Parameter(hidden = true) HttpServletRequest request) {
        return ok(stamps.revokeQr(boothId, body.token(), BearerTokens.require(authorization), cookies.readRefreshToken(request)));
    }
    @PostMapping("/users/{userUuid}/grant")
    @Operation(summary = "사용자 검색 결과로 스탬프 임의 지급", description = "ADMIN 또는 SUPER_ADMIN만 사용할 수 있습니다.")
    ResponseEntity<StampTargetResponse> grantUser(@PathVariable Long boothId, @PathVariable UUID userUuid,
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authorization,
            @Parameter(hidden = true) HttpServletRequest request) {
        return ok(stamps.grantBySearch(boothId, userUuid, BearerTokens.require(authorization), cookies.readRefreshToken(request)));
    }
    @PostMapping("/users/{userUuid}/revoke")
    @Operation(summary = "사용자 검색 결과로 스탬프 임의 회수", description = "ADMIN 또는 SUPER_ADMIN만 사용할 수 있습니다.")
    ResponseEntity<StampTargetResponse> revokeUser(@PathVariable Long boothId, @PathVariable UUID userUuid,
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authorization,
            @Parameter(hidden = true) HttpServletRequest request) {
        return ok(stamps.revokeBySearch(boothId, userUuid, BearerTokens.require(authorization), cookies.readRefreshToken(request)));
    }
    @GetMapping("/history")
    @Operation(summary = "부스별 현재 지급 현황과 지급·회수 이력",
            description = "현재 지급 현황과 페이지 단위 감사 이력을 반환합니다. BOOTH_MANAGER는 담당 부스만 조회할 수 있으며 사용자 정보가 마스킹됩니다. ADMIN 이상은 전체 부스를 조회합니다.")
    ResponseEntity<BoothStampAdminResponse> history(@PathVariable Long boothId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size,
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authorization,
            @Parameter(hidden = true) HttpServletRequest request) {
        return ok(stamps.adminHistory(boothId, page, size, BearerTokens.require(authorization),
                cookies.readRefreshToken(request)));
    }
    private <T> ResponseEntity<T> ok(AuthorizedResult<T> result) {
        ResponseEntity.BodyBuilder response = ResponseEntity.ok();
        if (result.newAccessToken() != null) response.header(UserController.REFRESHED_ACCESS_TOKEN, result.newAccessToken());
        if (result.newRefreshToken() != null) response.header(TokenCookieManager.SET_COOKIE, cookies.create(result.newRefreshToken()));
        return response.body(result.body());
    }
}
