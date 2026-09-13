package org.syu_likelion.Festa_2026.bamboo;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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
import org.syu_likelion.Festa_2026.bamboo.BambooDtos.BambooAdminPageResponse;
import org.syu_likelion.Festa_2026.bamboo.BambooDtos.BambooAuthorResponse;
import org.syu_likelion.Festa_2026.bamboo.BambooDtos.BambooMuteRequest;
import org.syu_likelion.Festa_2026.bamboo.BambooDtos.BambooMuteResponse;
import org.syu_likelion.Festa_2026.bamboo.BambooDtos.BambooNicknameResponse;
import org.syu_likelion.Festa_2026.bamboo.BambooDtos.BambooRenameRequest;
import org.syu_likelion.Festa_2026.bamboo.BambooDtos.BambooSettingsRequest;
import org.syu_likelion.Festa_2026.bamboo.BambooDtos.BambooSettingsResponse;
import org.syu_likelion.Festa_2026.bamboo.BambooDtos.BambooStatusChangeRequest;
import org.syu_likelion.Festa_2026.user.UserController;

/**
 * 대나무숲 관리 API.
 *
 * <p>경로가 {@code /api/admin/bamboo} 이므로 채팅용 동시 요청 상한의 적용을 받지 않는다.
 * 채팅이 폭주해 사용자 요청이 거절되는 상황에서도 킬스위치는 반드시 동작해야 한다.
 */
@RestController
@RequestMapping("/api/admin/bamboo")
@Tag(name = "Bamboo Admin", description = "대나무숲 신고 처리와 운영 설정")
public class BambooAdminController {
    private final BambooAdminService admin;
    private final TokenCookieManager cookies;

    public BambooAdminController(BambooAdminService admin, TokenCookieManager cookies) {
        this.admin = admin;
        this.cookies = cookies;
    }

    @GetMapping("/reports")
    @Operation(summary = "신고된 메시지 목록", description = "신고 수 내림차순입니다. STAFF 이상.")
    @SecurityRequirement(name = "bearerAuth")
    ResponseEntity<BambooAdminPageResponse> reports(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size,
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authorization,
            @Parameter(hidden = true) HttpServletRequest request) {
        return response(admin.reports(BearerTokens.require(authorization),
                cookies.readRefreshToken(request), page, size));
    }

    @PatchMapping("/messages")
    @Operation(summary = "메시지 상태 일괄 변경",
            description = "VISIBLE 로 되돌리거나 HIDDEN·DELETED 로 가립니다. 물리 삭제는 없습니다. STAFF 이상.")
    @SecurityRequirement(name = "bearerAuth")
    ResponseEntity<Map<String, Integer>> changeStatus(
            @Valid @RequestBody BambooStatusChangeRequest body,
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authorization,
            @Parameter(hidden = true) HttpServletRequest request) {
        AuthorizedResult<Integer> result = admin.changeStatus(BearerTokens.require(authorization),
                cookies.readRefreshToken(request), body.ids(), body.status());
        return ResponseEntity.ok().headers(headers(result)).body(Map.of("changed", result.body()));
    }

    @PostMapping("/messages/{id}/mute-author")
    @Operation(summary = "작성자 작성 차단",
            description = "메시지를 지목해 그 작성자의 작성을 차단합니다. minutes 가 0 이면 해제합니다. "
                    + "사유는 필수이며 감사 이력에 남습니다. 작성자 신원을 몰라도 조치할 수 있습니다. ADMIN 이상.")
    @SecurityRequirement(name = "bearerAuth")
    ResponseEntity<BambooMuteResponse> muteAuthor(
            @PathVariable Long id,
            @Valid @RequestBody BambooMuteRequest body,
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authorization,
            @Parameter(hidden = true) HttpServletRequest request) {
        return response(admin.muteAuthor(BearerTokens.require(authorization),
                cookies.readRefreshToken(request), id, body.minutes(), body.reason()));
    }

    @PatchMapping("/messages/{id}/author-nickname")
    @Operation(summary = "작성자 닉네임 강제 변경",
            description = "부적절한 닉네임을 교체합니다. 과거 메시지의 표시 이름도 함께 바뀝니다. STAFF 이상.")
    @SecurityRequirement(name = "bearerAuth")
    ResponseEntity<BambooNicknameResponse> renameAuthor(
            @PathVariable Long id,
            @Valid @RequestBody BambooRenameRequest body,
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authorization,
            @Parameter(hidden = true) HttpServletRequest request) {
        return response(admin.renameAuthor(BearerTokens.require(authorization),
                cookies.readRefreshToken(request), id, body.nickname()));
    }

    @GetMapping("/messages/{id}/author")
    @Operation(summary = "작성자 신원 조회",
            description = "SUPER_ADMIN 전용입니다. 누가 언제 누구를 조회했는지 감사 로그에 남습니다.")
    @SecurityRequirement(name = "bearerAuth")
    ResponseEntity<BambooAuthorResponse> author(
            @PathVariable Long id,
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authorization,
            @Parameter(hidden = true) HttpServletRequest request) {
        return response(admin.author(BearerTokens.require(authorization),
                cookies.readRefreshToken(request), id));
    }

    @GetMapping("/settings")
    @Operation(summary = "운영 설정 조회", description = "STAFF 이상이 대나무숲의 개방 여부(enabled), 읽기 전용 여부(readOnly), 종료 시각(closesAt)을 조회합니다.")
    @SecurityRequirement(name = "bearerAuth")
    ResponseEntity<BambooSettingsResponse> settings(
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authorization,
            @Parameter(hidden = true) HttpServletRequest request) {
        return response(admin.settings(BearerTokens.require(authorization),
                cookies.readRefreshToken(request)));
    }

    @PatchMapping("/settings")
    @Operation(summary = "운영 설정 변경",
            description = "킬스위치(enabled), 읽기 전용(readOnly), 종료 시각(closesAt)을 바꿉니다. ADMIN 이상.")
    @SecurityRequirement(name = "bearerAuth")
    ResponseEntity<BambooSettingsResponse> updateSettings(
            @RequestBody BambooSettingsRequest body,
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authorization,
            @Parameter(hidden = true) HttpServletRequest request) {
        return response(admin.updateSettings(BearerTokens.require(authorization),
                cookies.readRefreshToken(request), body.enabled(), body.readOnly(), body.closesAt(),
                body.clearClosesAt()));
    }

    private <T> ResponseEntity<T> response(AuthorizedResult<T> result) {
        return ResponseEntity.ok().headers(headers(result)).body(result.body());
    }

    private HttpHeaders headers(AuthorizedResult<?> result) {
        HttpHeaders headers = new HttpHeaders();
        if (result.newAccessToken() != null) headers.add(UserController.REFRESHED_ACCESS_TOKEN, result.newAccessToken());
        if (result.newRefreshToken() != null) headers.add(TokenCookieManager.SET_COOKIE, cookies.create(result.newRefreshToken()));
        return headers;
    }
}
