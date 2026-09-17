package org.syu_likelion.Festa_2026.bamboo;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.syu_likelion.Festa_2026.auth.AuthorizedSsoExecutor.AuthorizedResult;
import org.syu_likelion.Festa_2026.auth.BearerTokens;
import org.syu_likelion.Festa_2026.auth.TokenCookieManager;
import org.syu_likelion.Festa_2026.bamboo.BambooDtos.BambooMessageCreateRequest;
import org.syu_likelion.Festa_2026.bamboo.BambooDtos.BambooMessageResponse;
import org.syu_likelion.Festa_2026.bamboo.BambooDtos.BambooNicknameRequest;
import org.syu_likelion.Festa_2026.bamboo.BambooDtos.BambooReportRequest;
import org.syu_likelion.Festa_2026.bamboo.BambooDtos.BambooNicknameResponse;
import org.syu_likelion.Festa_2026.bamboo.BambooDtos.BambooRoomResponse;
import org.syu_likelion.Festa_2026.bamboo.BambooDtos.BambooStreamResponse;
import org.syu_likelion.Festa_2026.error.ApiException;
import org.syu_likelion.Festa_2026.user.UserController;

@RestController
@RequestMapping("/api/bamboo")
@Tag(name = "Bamboo", description = "학생 인증 필수 대나무숲 익명 오픈채팅. 미인증 시 403 SCHOOL_VERIFICATION_REQUIRED")
public class BambooController {
    private final BambooApiService api;
    private final TokenCookieManager cookies;

    public BambooController(BambooApiService api, TokenCookieManager cookies) {
        this.api = api;
        this.cookies = cookies;
    }

    @GetMapping
    @Operation(summary = "대나무숲 상태 조회",
            description = "방 개방 여부, 읽기 전용 여부, 내 닉네임과 현재 커서를 반환합니다.")
    @SecurityRequirement(name = "bearerAuth")
    ResponseEntity<BambooRoomResponse> room(
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authorization,
            @Parameter(hidden = true) HttpServletRequest request) {
        return response(api.room(BearerTokens.require(authorization), cookies.readRefreshToken(request)));
    }

    @GetMapping("/nickname/suggest")
    @Operation(summary = "랜덤 닉네임 후보 조회",
            description = "현재 사용 중이 아닌 닉네임을 하나 제안합니다. 확정 전까지 선점되지 않습니다.")
    @SecurityRequirement(name = "bearerAuth")
    ResponseEntity<BambooNicknameResponse> suggestNickname(
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authorization,
            @Parameter(hidden = true) HttpServletRequest request) {
        return response(api.suggestNickname(BearerTokens.require(authorization),
                cookies.readRefreshToken(request)));
    }

    @PostMapping("/nickname")
    @ApiResponse(responseCode = "201", description = "생성 완료", useReturnTypeSchema = true)
    @Operation(summary = "닉네임 확정",
            description = "닉네임은 한 번만 정할 수 있고 이후 변경할 수 없습니다. 중복은 허용되지 않습니다.")
    @SecurityRequirement(name = "bearerAuth")
    ResponseEntity<BambooNicknameResponse> claimNickname(
            @Valid @RequestBody BambooNicknameRequest body,
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authorization,
            @Parameter(hidden = true) HttpServletRequest request) {
        AuthorizedResult<BambooNicknameResponse> result = api.claimNickname(
                BearerTokens.require(authorization), cookies.readRefreshToken(request), body.nickname());
        return ResponseEntity.status(HttpStatus.CREATED).headers(headers(result)).body(result.body());
    }

    @GetMapping("/messages")
    @Operation(summary = "메시지 조회",
            description = "after 를 주면 해당 커서 이후의 신규·변경 메시지를, before 를 주면 그 id 이전의 과거 메시지를 "
                    + "반환합니다. 두 값을 함께 보낼 수 없습니다. 둘 다 없으면 과거 목록이 아니라 현재 cursor와 빈 messages를 반환합니다. "
                    + "최초 과거 조회는 before=9223372036854775807을 문자열 그대로 전송합니다(JS Number로 만들지 않음). "
                    + "응답 cursor는 after 폴링에 사용하고 추가 과거 조회에는 가장 오래된 메시지 id를 before로 보냅니다. 진행 중 폴링 cursor를 추가 과거 조회 응답으로 덮어쓰지 않습니다. "
                    + "신고 5회 이상은 HIDDEN과 원문, 관리자 차단은 BLOCKED와 null 본문을 반환합니다. "
                    + "과거 목록에도 HIDDEN·BLOCKED를 포함하고 DELETED는 제외합니다.")
    @SecurityRequirement(name = "bearerAuth")
    ResponseEntity<BambooStreamResponse> messages(
            @RequestParam(required = false) Long after,
            @RequestParam(required = false) Long before,
            @RequestParam(required = false) Integer size,
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authorization,
            @Parameter(hidden = true) HttpServletRequest request) {
        // 인증을 파라미터 검증보다 먼저 한다. 비로그인 요청이 400 을 받으면 안 된다.
        String access = BearerTokens.require(authorization);
        String refresh = cookies.readRefreshToken(request);
        if (after != null && before != null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER",
                    "after 와 before 는 함께 사용할 수 없습니다.");
        }
        if (before != null) return response(api.history(access, refresh, before, size));
        return response(api.stream(access, refresh, after, size));
    }

    @PostMapping("/messages")
    @ApiResponse(responseCode = "201", description = "생성 완료", useReturnTypeSchema = true)
    @Operation(summary = "메시지 작성", description = "닉네임을 먼저 정해야 작성할 수 있습니다.")
    @SecurityRequirement(name = "bearerAuth")
    ResponseEntity<BambooMessageResponse> create(
            @Valid @RequestBody BambooMessageCreateRequest body,
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authorization,
            @Parameter(hidden = true) HttpServletRequest request) {
        AuthorizedResult<BambooMessageResponse> result = api.create(BearerTokens.require(authorization),
                cookies.readRefreshToken(request), body.content());
        return ResponseEntity.status(HttpStatus.CREATED).headers(headers(result)).body(result.body());
    }

    @PostMapping("/messages/{id}/report")
    @ApiResponse(responseCode = "204", description = "처리 완료, 응답 본문 없음", content = @Content)
    @Operation(summary = "메시지 신고",
            description = "본인이 작성한 메시지는 신고할 수 없고, 같은 메시지를 두 번 신고할 수 없습니다.")
    @SecurityRequirement(name = "bearerAuth")
    ResponseEntity<Void> report(
            @PathVariable Long id,
            @Valid @RequestBody BambooReportRequest body,
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authorization,
            @Parameter(hidden = true) HttpServletRequest request) {
        AuthorizedResult<Void> result = api.report(BearerTokens.require(authorization),
                cookies.readRefreshToken(request), id, body.reason());
        return ResponseEntity.noContent().headers(headers(result)).build();
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
