package org.syu_likelion.Festa_2026.birthday;

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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.syu_likelion.Festa_2026.auth.AuthorizedSsoExecutor.AuthorizedResult;
import org.syu_likelion.Festa_2026.auth.BearerTokens;
import org.syu_likelion.Festa_2026.auth.TokenCookieManager;
import org.syu_likelion.Festa_2026.birthday.BirthdayMessageDtos.BirthdayMessageCreateRequest;
import org.syu_likelion.Festa_2026.birthday.BirthdayMessageDtos.BirthdayMessagePageResponse;
import org.syu_likelion.Festa_2026.birthday.BirthdayMessageDtos.BirthdayMessageResponse;
import org.syu_likelion.Festa_2026.birthday.BirthdayMessageDtos.HeartResponse;
import org.syu_likelion.Festa_2026.birthday.BirthdayMessageDtos.MyBirthdayMessageResponse;
import org.syu_likelion.Festa_2026.user.UserController;

@RestController
@RequestMapping("/api/birthday-messages")
@Tag(name = "Birthday Message", description = "수야·수호 생일축하 쪽지 게시판")
public class BirthdayMessageController {
    private final BirthdayMessageService messages;
    private final BirthdayMessageApiService api;
    private final TokenCookieManager cookies;

    public BirthdayMessageController(BirthdayMessageService messages, BirthdayMessageApiService api,
                                     TokenCookieManager cookies) {
        this.messages = messages;
        this.api = api;
        this.cookies = cookies;
    }

    @GetMapping
    @Operation(summary = "생일축하 쪽지 목록 조회", description = "로그인 없이 조회할 수 있습니다.")
    ResponseEntity<BirthdayMessagePageResponse> list(
            @RequestParam(defaultValue = "LATEST") BirthdayMessageSort sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size,
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authorization,
            @Parameter(hidden = true) HttpServletRequest request) {
        if (!hasBearer(authorization)) return ResponseEntity.ok(messages.list(null, sort, page, size));
        return response(api.list(BearerTokens.require(authorization), cookies.readRefreshToken(request),
                sort, page, size));
    }

    @GetMapping("/{id}")
    @Operation(summary = "생일축하 쪽지 상세 조회", description = "로그인 없이 조회할 수 있습니다.")
    ResponseEntity<BirthdayMessageResponse> detail(
            @PathVariable Long id,
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authorization,
            @Parameter(hidden = true) HttpServletRequest request) {
        if (!hasBearer(authorization)) return ResponseEntity.ok(messages.get(id, null));
        return response(api.get(id, BearerTokens.require(authorization), cookies.readRefreshToken(request)));
    }

    @GetMapping("/me")
    @Operation(summary = "내 생일축하 쪽지 조회",
            description = "Bearer 인증이 필요합니다. 현재 로그인한 사용자의 활성 쪽지와 작성 여부를 조회합니다.")
    @SecurityRequirement(name = "bearerAuth")
    ResponseEntity<MyBirthdayMessageResponse> mine(
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authorization,
            @Parameter(hidden = true) HttpServletRequest request) {
        return response(api.getMine(BearerTokens.require(authorization), cookies.readRefreshToken(request)));
    }

    @PostMapping
    @ApiResponse(responseCode = "201", description = "생성 완료", useReturnTypeSchema = true)
    @Operation(summary = "생일축하 쪽지 작성", description = "활성 쪽지는 한 사람당 하나만 작성할 수 있습니다.")
    @SecurityRequirement(name = "bearerAuth")
    ResponseEntity<BirthdayMessageResponse> create(
            @Valid @RequestBody BirthdayMessageCreateRequest body,
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authorization,
            @Parameter(hidden = true) HttpServletRequest request) {
        AuthorizedResult<BirthdayMessageResponse> result = api.create(BearerTokens.require(authorization),
                cookies.readRefreshToken(request), body);
        return ResponseEntity.status(HttpStatus.CREATED).headers(headers(result)).body(result.body());
    }

    @DeleteMapping("/{id}")
    @ApiResponse(responseCode = "204", description = "처리 완료, 응답 본문 없음", content = @Content)
    @Operation(summary = "내 생일축하 쪽지 삭제", description = "삭제 후 새 쪽지를 다시 작성할 수 있습니다.")
    @SecurityRequirement(name = "bearerAuth")
    ResponseEntity<Void> delete(
            @PathVariable Long id,
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authorization,
            @Parameter(hidden = true) HttpServletRequest request) {
        AuthorizedResult<Void> result = api.delete(id, BearerTokens.require(authorization),
                cookies.readRefreshToken(request));
        return ResponseEntity.noContent().headers(headers(result)).build();
    }

    @PutMapping("/{id}/heart")
    @Operation(summary = "생일축하 쪽지 하트 추가",
            description = "로그인한 사용자의 하트를 추가하고 하트 수와 내 하트 여부를 반환합니다. 같은 사용자가 반복 요청해도 하트는 중복 추가되지 않습니다.")
    @SecurityRequirement(name = "bearerAuth")
    ResponseEntity<HeartResponse> addHeart(
            @PathVariable Long id,
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authorization,
            @Parameter(hidden = true) HttpServletRequest request) {
        return response(api.addHeart(id, BearerTokens.require(authorization), cookies.readRefreshToken(request)));
    }

    @DeleteMapping("/{id}/heart")
    @Operation(summary = "생일축하 쪽지 하트 취소",
            description = "로그인한 사용자의 하트를 취소하고 하트 수와 내 하트 여부를 반환합니다.")
    @SecurityRequirement(name = "bearerAuth")
    ResponseEntity<HeartResponse> removeHeart(
            @PathVariable Long id,
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authorization,
            @Parameter(hidden = true) HttpServletRequest request) {
        return response(api.removeHeart(id, BearerTokens.require(authorization), cookies.readRefreshToken(request)));
    }

    private boolean hasBearer(String authorization) {
        return authorization != null && !authorization.isBlank();
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
