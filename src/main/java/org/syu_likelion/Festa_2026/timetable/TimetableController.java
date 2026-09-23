package org.syu_likelion.Festa_2026.timetable;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.syu_likelion.Festa_2026.auth.AuthorizedSsoExecutor.AuthorizedResult;
import org.syu_likelion.Festa_2026.auth.BearerTokens;
import org.syu_likelion.Festa_2026.auth.TokenCookieManager;
import org.syu_likelion.Festa_2026.timetable.TimetableDtos.MutationRequest;
import org.syu_likelion.Festa_2026.timetable.TimetableDtos.ScheduleResponse;
import org.syu_likelion.Festa_2026.user.UserController;

@RestController
@RequestMapping("/api/timetable")
@Tag(name = "Timetable", description = "타임테이블 일정과 공개 시각 관리")
public class TimetableController {
    private final TimetableService timetable;
    private final TokenCookieManager cookies;

    public TimetableController(TimetableService timetable, TokenCookieManager cookies) {
        this.timetable = timetable;
        this.cookies = cookies;
    }

    @GetMapping
    @Operation(summary = "타임테이블 일정 목록 조회",
            description = "로그인 없이 시작순 조회. 공개 전에도 시간은 반환하며 제목은 TBA, 공연팀은 null입니다.")
    ResponseEntity<List<ScheduleResponse>> list() {
        return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore())
                .body(timetable.list());
    }

    @GetMapping("/admin")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "관리자 전체 일정 조회", description = "ADMIN 이상. 공개 전 일정명과 공연팀 연결을 포함합니다.")
    ResponseEntity<List<ScheduleResponse>> adminList(
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authorization,
            @Parameter(hidden = true) HttpServletRequest request) {
        return response(timetable.listAdmin(BearerTokens.require(authorization), cookies.readRefreshToken(request)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "타임테이블 일정 상세 조회",
            description = "로그인 없이 조회합니다. 공개 전에는 제목이 TBA이고 공연팀 연결 정보는 숨깁니다.")
    ResponseEntity<ScheduleResponse> detail(@PathVariable Long id) {
        return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore())
                .body(timetable.detail(id));
    }

    @SecurityRequirement(name = "bearerAuth")
    @PostMapping
    @ApiResponse(responseCode = "201", description = "생성 완료", useReturnTypeSchema = true)
    @Operation(summary = "일정 등록",
            description = "ADMIN 이상이 일정과 선택적 공연팀 연결, 공개 시각을 등록합니다.")
    ResponseEntity<ScheduleResponse> create(
            @Valid @RequestBody MutationRequest body,
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authorization,
            @Parameter(hidden = true) HttpServletRequest request) {
        AuthorizedResult<ScheduleResponse> result = timetable.create(BearerTokens.require(authorization),
                cookies.readRefreshToken(request), body);
        return ResponseEntity.status(HttpStatus.CREATED)
                .headers(headers(result))
                .body(result.body());
    }

    @SecurityRequirement(name = "bearerAuth")
    @PatchMapping("/{id}")
    @Operation(summary = "일정 수정",
            description = "ADMIN 이상이 전체 필드를 전달해 수정합니다. performanceId가 null이면 연결을 해제합니다.")
    ResponseEntity<ScheduleResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody MutationRequest body,
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authorization,
            @Parameter(hidden = true) HttpServletRequest request) {
        return response(timetable.update(id, BearerTokens.require(authorization),
                cookies.readRefreshToken(request), body));
    }

    @SecurityRequirement(name = "bearerAuth")
    @DeleteMapping("/{id}")
    @ApiResponse(responseCode = "204", description = "처리 완료, 응답 본문 없음", content = @Content)
    @Operation(summary = "일정 삭제",
            description = "ADMIN 이상이 일정을 삭제합니다.")
    ResponseEntity<Void> delete(
            @PathVariable Long id,
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authorization,
            @Parameter(hidden = true) HttpServletRequest request) {
        AuthorizedResult<Void> result = timetable.delete(id, BearerTokens.require(authorization),
                cookies.readRefreshToken(request));
        return ResponseEntity.noContent().headers(headers(result)).build();
    }

    private <T> ResponseEntity<T> response(AuthorizedResult<T> result) {
        return ResponseEntity.ok().headers(headers(result)).body(result.body());
    }

    private org.springframework.http.HttpHeaders headers(AuthorizedResult<?> result) {
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setCacheControl("no-store");
        if (result.newAccessToken() != null) {
            headers.add(UserController.REFRESHED_ACCESS_TOKEN, result.newAccessToken());
        }
        if (result.newRefreshToken() != null) {
            headers.add(TokenCookieManager.SET_COOKIE, cookies.create(result.newRefreshToken()));
        }
        return headers;
    }
}
