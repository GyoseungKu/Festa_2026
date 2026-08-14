package org.syu_likelion.Festa_2026.birthday;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.syu_likelion.Festa_2026.auth.AuthorizedSsoExecutor.AuthorizedResult;
import org.syu_likelion.Festa_2026.auth.BearerTokens;
import org.syu_likelion.Festa_2026.auth.TokenCookieManager;
import org.syu_likelion.Festa_2026.birthday.BirthdayMessageDtos.AdminBirthdayMessagePageResponse;
import org.syu_likelion.Festa_2026.birthday.BirthdayMessageDtos.AdminHeartPageResponse;
import org.syu_likelion.Festa_2026.user.UserController;

@RestController
@RequestMapping("/api/admin/birthday-messages")
@Tag(name = "Birthday Message Admin", description = "STAFF 이상 생일축하 쪽지 관리")
@SecurityRequirement(name = "bearerAuth")
public class BirthdayMessageAdminController {
    private final BirthdayMessageAdminService admin;
    private final TokenCookieManager cookies;

    public BirthdayMessageAdminController(BirthdayMessageAdminService admin, TokenCookieManager cookies) {
        this.admin = admin;
        this.cookies = cookies;
    }

    @GetMapping
    @Operation(summary = "작성자 신원을 포함한 생일축하 쪽지 목록")
    ResponseEntity<AdminBirthdayMessagePageResponse> list(
            @RequestParam(defaultValue = "LATEST") BirthdayMessageSort sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size,
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authorization,
            @Parameter(hidden = true) HttpServletRequest request) {
        return response(admin.list(BearerTokens.require(authorization), cookies.readRefreshToken(request),
                sort, page, size));
    }

    @GetMapping("/{id}/hearts")
    @Operation(summary = "쪽지에 하트를 누른 사용자 목록")
    ResponseEntity<AdminHeartPageResponse> hearts(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size,
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authorization,
            @Parameter(hidden = true) HttpServletRequest request) {
        return response(admin.hearts(id, BearerTokens.require(authorization), cookies.readRefreshToken(request),
                page, size));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "생일축하 쪽지 관리자 삭제")
    ResponseEntity<Void> delete(
            @PathVariable Long id,
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authorization,
            @Parameter(hidden = true) HttpServletRequest request) {
        AuthorizedResult<Void> result = admin.delete(id, BearerTokens.require(authorization),
                cookies.readRefreshToken(request));
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
