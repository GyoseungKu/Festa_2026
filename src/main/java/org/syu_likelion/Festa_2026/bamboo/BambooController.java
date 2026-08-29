package org.syu_likelion.Festa_2026.bamboo;

import io.swagger.v3.oas.annotations.Operation;
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
import org.syu_likelion.Festa_2026.bamboo.BambooDtos.BambooNicknameResponse;
import org.syu_likelion.Festa_2026.bamboo.BambooDtos.BambooRoomResponse;
import org.syu_likelion.Festa_2026.bamboo.BambooDtos.BambooStreamResponse;
import org.syu_likelion.Festa_2026.error.ApiException;
import org.syu_likelion.Festa_2026.user.UserController;

@RestController
@RequestMapping("/api/bamboo")
@Tag(name = "Bamboo", description = "대나무숲 익명 오픈채팅")
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
                    + "반환합니다. 두 값을 함께 보낼 수 없습니다. 응답의 cursor 는 다음 조회 시작점입니다.")
    @SecurityRequirement(name = "bearerAuth")
    ResponseEntity<BambooStreamResponse> messages(
            @RequestParam(required = false) Long after,
            @RequestParam(required = false) Long before,
            @RequestParam(required = false) Integer size,
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authorization,
            @Parameter(hidden = true) HttpServletRequest request) {
        if (after != null && before != null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER",
                    "after 와 before 는 함께 사용할 수 없습니다.");
        }
        String access = BearerTokens.require(authorization);
        String refresh = cookies.readRefreshToken(request);
        if (before != null) return response(api.history(access, refresh, before, size));
        return response(api.stream(access, refresh, after, size));
    }

    @PostMapping("/messages")
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
