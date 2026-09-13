package org.syu_likelion.Festa_2026.performance;

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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.syu_likelion.Festa_2026.auth.AuthorizedSsoExecutor.AuthorizedResult;
import org.syu_likelion.Festa_2026.auth.BearerTokens;
import org.syu_likelion.Festa_2026.auth.TokenCookieManager;
import org.syu_likelion.Festa_2026.performance.PerformanceDtos.PerformanceMutationRequest;
import org.syu_likelion.Festa_2026.performance.PerformanceDtos.PerformanceResponse;
import org.syu_likelion.Festa_2026.user.UserController;

@RestController
@RequestMapping("/api/performances")
@Tag(name = "Performance", description = "축제 무대 공연팀과 공개 일정 관리")
@SecurityRequirement(name = "bearerAuth")
public class PerformanceController {
    private final PerformanceService performances;
    private final TokenCookieManager cookies;

    public PerformanceController(PerformanceService performances, TokenCookieManager cookies) {
        this.performances = performances;
        this.cookies = cookies;
    }

    @GetMapping
    @Operation(summary = "공개된 공연팀 목록 조회",
            description = "로그인한 사용자가 공개일시가 지난 연예인·동아리·개인 공연팀을 공연 시작순으로 조회합니다.")
    ResponseEntity<List<PerformanceResponse>> list(
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authorization,
            @Parameter(hidden = true) HttpServletRequest request) {
        return response(performances.listVisible(BearerTokens.require(authorization),
                cookies.readRefreshToken(request)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "공개된 공연팀 상세 조회",
            description = "로그인한 사용자가 공개일시가 지난 공연팀 하나를 조회합니다.")
    ResponseEntity<PerformanceResponse> detail(
            @PathVariable Long id,
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authorization,
            @Parameter(hidden = true) HttpServletRequest request) {
        return response(performances.getVisible(id, BearerTokens.require(authorization),
                cookies.readRefreshToken(request)));
    }

    @PostMapping
    @ApiResponse(responseCode = "201", description = "생성 완료", useReturnTypeSchema = true)
    @Operation(summary = "공연팀 등록",
            description = "ADMIN 또는 SUPER_ADMIN이 공연팀과 링크 첨부를 등록합니다. 파일은 등록 후 파일 업로드 API를 사용합니다.")
    ResponseEntity<PerformanceResponse> create(
            @Valid @RequestBody PerformanceMutationRequest body,
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authorization,
            @Parameter(hidden = true) HttpServletRequest request) {
        AuthorizedResult<PerformanceResponse> result = performances.create(BearerTokens.require(authorization),
                cookies.readRefreshToken(request), body);
        return ResponseEntity.status(HttpStatus.CREATED)
                .headers(headers(result))
                .body(result.body());
    }

    @PatchMapping("/{id}")
    @Operation(summary = "공연팀 수정",
            description = "ADMIN 또는 SUPER_ADMIN이 기본 정보와 링크 첨부를 수정합니다. 기존 업로드 파일은 유지됩니다.")
    ResponseEntity<PerformanceResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody PerformanceMutationRequest body,
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authorization,
            @Parameter(hidden = true) HttpServletRequest request) {
        return response(performances.update(id, BearerTokens.require(authorization),
                cookies.readRefreshToken(request), body));
    }

    @PostMapping(value = "/{id}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "공연팀 이미지 파일 업로드",
            description = "ADMIN 또는 SUPER_ADMIN이 이미지 파일을 추가합니다. 링크와 파일을 합해 최대 3개입니다.")
    ResponseEntity<PerformanceResponse> uploadImages(
            @PathVariable Long id, @RequestPart("files") List<MultipartFile> files,
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authorization,
            @Parameter(hidden = true) HttpServletRequest request) {
        return response(performances.upload(id, PerformanceMediaKind.IMAGE, files,
                BearerTokens.require(authorization), cookies.readRefreshToken(request)));
    }

    @PostMapping(value = "/{id}/videos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "공연팀 동영상 파일 업로드",
            description = "ADMIN 또는 SUPER_ADMIN이 동영상 파일을 추가합니다. 링크와 파일을 합해 최대 3개입니다.")
    ResponseEntity<PerformanceResponse> uploadVideos(
            @PathVariable Long id, @RequestPart("files") List<MultipartFile> files,
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authorization,
            @Parameter(hidden = true) HttpServletRequest request) {
        return response(performances.upload(id, PerformanceMediaKind.VIDEO, files,
                BearerTokens.require(authorization), cookies.readRefreshToken(request)));
    }

    @DeleteMapping("/{id}/media/{mediaId}")
    @Operation(summary = "공연팀 미디어 삭제",
            description = "ADMIN 또는 SUPER_ADMIN이 등록된 이미지 또는 동영상 한 개를 삭제합니다.")
    ResponseEntity<PerformanceResponse> deleteMedia(
            @PathVariable Long id, @PathVariable Long mediaId,
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authorization,
            @Parameter(hidden = true) HttpServletRequest request) {
        return response(performances.deleteMedia(id, mediaId, BearerTokens.require(authorization),
                cookies.readRefreshToken(request)));
    }

    @DeleteMapping("/{id}")
    @ApiResponse(responseCode = "204", description = "처리 완료, 응답 본문 없음", content = @Content)
    @Operation(summary = "공연팀 삭제",
            description = "ADMIN 또는 SUPER_ADMIN이 공연팀과 연결된 업로드 파일을 삭제합니다.")
    ResponseEntity<Void> delete(
            @PathVariable Long id,
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authorization,
            @Parameter(hidden = true) HttpServletRequest request) {
        AuthorizedResult<Void> result = performances.delete(id, BearerTokens.require(authorization),
                cookies.readRefreshToken(request));
        return ResponseEntity.noContent().headers(headers(result)).build();
    }

    private <T> ResponseEntity<T> response(AuthorizedResult<T> result) {
        return ResponseEntity.ok().headers(headers(result)).body(result.body());
    }

    private org.springframework.http.HttpHeaders headers(AuthorizedResult<?> result) {
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        if (result.newAccessToken() != null) {
            headers.add(UserController.REFRESHED_ACCESS_TOKEN, result.newAccessToken());
        }
        if (result.newRefreshToken() != null) {
            headers.add(TokenCookieManager.SET_COOKIE, cookies.create(result.newRefreshToken()));
        }
        return headers;
    }
}
