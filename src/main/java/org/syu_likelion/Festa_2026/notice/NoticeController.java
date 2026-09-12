package org.syu_likelion.Festa_2026.notice;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpHeaders;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.syu_likelion.Festa_2026.auth.AuthorizedSsoExecutor.AuthorizedResult;
import org.syu_likelion.Festa_2026.auth.BearerTokens;
import org.syu_likelion.Festa_2026.auth.TokenCookieManager;
import org.syu_likelion.Festa_2026.notice.NoticeDtos.NoticeMutationRequest;
import org.syu_likelion.Festa_2026.notice.NoticeDtos.NoticePageResponse;
import org.syu_likelion.Festa_2026.notice.NoticeDtos.NoticePinUpdateRequest;
import org.syu_likelion.Festa_2026.notice.NoticeDtos.NoticeResponse;
import org.syu_likelion.Festa_2026.user.UserController;

@RestController
@RequestMapping("/api/notices")
@Tag(name = "Notice", description = "로그인 없이 조회할 수 있는 일반 공지")
public class NoticeController {
    private final NoticeService notices;
    private final NoticeApiService api;
    private final TokenCookieManager cookies;

    public NoticeController(NoticeService notices, NoticeApiService api, TokenCookieManager cookies) {
        this.notices = notices;
        this.api = api;
        this.cookies = cookies;
    }

    @GetMapping
    @Operation(summary = "일반 공지 목록 조회", description = "최상단 배너, 상단 고정, 일반 공지 순으로 조회합니다. bannerOnly=true이면 배너 공지만 조회합니다.")
    ResponseEntity<NoticePageResponse> list(
            @RequestParam(defaultValue = "NEWEST") NoticeSort sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "false") boolean bannerOnly) {
        return ResponseEntity.ok(notices.listPublic(sort, page, size, bannerOnly));
    }

    @GetMapping("/{id}")
    @Operation(summary = "일반 공지 상세 조회", description = "호출할 때마다 조회수가 1 증가합니다.")
    ResponseEntity<NoticeResponse> detail(@PathVariable Long id) {
        return ResponseEntity.ok(notices.getPublic(id));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "일반 공지 등록", description = "STAFF 이상이 공지와 첨부파일을 등록합니다. data.banner=true이면 최상단 배너 공지로 등록합니다. pinned와 별개이며 여러 배너를 등록할 수 있습니다.")
    @SecurityRequirement(name = "bearerAuth")
    ResponseEntity<NoticeResponse> create(
            @Valid @RequestPart("data") NoticeMutationRequest data,
            @RequestPart(value = "media", required = false) List<MultipartFile> media,
            @RequestPart(value = "files", required = false) List<MultipartFile> files,
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authorization,
            @Parameter(hidden = true) HttpServletRequest request) {
        AuthorizedResult<NoticeResponse> result = api.create(BearerTokens.require(authorization),
                cookies.readRefreshToken(request), data, media, files);
        return ResponseEntity.status(HttpStatus.CREATED).headers(headers(result)).body(result.body());
    }

    @PatchMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "일반 공지 수정", description = "STAFF 이상이 내용과 첨부파일 구성을 수정합니다. data.banner로 최상단 배너를 설정하거나 해제하며, 생략하면 기존 설정을 유지합니다.")
    @SecurityRequirement(name = "bearerAuth")
    ResponseEntity<NoticeResponse> update(
            @PathVariable Long id,
            @Valid @RequestPart("data") NoticeMutationRequest data,
            @RequestParam(value = "removeAttachmentIds", required = false) List<Long> removeAttachmentIds,
            @RequestPart(value = "media", required = false) List<MultipartFile> media,
            @RequestPart(value = "files", required = false) List<MultipartFile> files,
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authorization,
            @Parameter(hidden = true) HttpServletRequest request) {
        return response(api.update(id, BearerTokens.require(authorization), cookies.readRefreshToken(request),
                data, removeAttachmentIds, media, files));
    }

    @PatchMapping("/{id}/pin")
    @Operation(summary = "일반 공지 상단 고정 변경")
    @SecurityRequirement(name = "bearerAuth")
    ResponseEntity<NoticeResponse> changePin(
            @PathVariable Long id, @Valid @RequestBody NoticePinUpdateRequest body,
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authorization,
            @Parameter(hidden = true) HttpServletRequest request) {
        return response(api.changePinned(id, BearerTokens.require(authorization),
                cookies.readRefreshToken(request), body.pinned()));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "일반 공지 삭제", description = "공지와 업로드된 첨부파일을 함께 삭제합니다.")
    @SecurityRequirement(name = "bearerAuth")
    ResponseEntity<Void> delete(
            @PathVariable Long id,
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authorization,
            @Parameter(hidden = true) HttpServletRequest request) {
        AuthorizedResult<Void> result = api.delete(id, BearerTokens.require(authorization),
                cookies.readRefreshToken(request));
        return ResponseEntity.noContent().headers(headers(result)).build();
    }

    private ResponseEntity<NoticeResponse> response(AuthorizedResult<NoticeResponse> result) {
        return ResponseEntity.ok().headers(headers(result)).body(result.body());
    }

    private HttpHeaders headers(AuthorizedResult<?> result) {
        HttpHeaders headers = new HttpHeaders();
        if (result.newAccessToken() != null) {
            headers.add(UserController.REFRESHED_ACCESS_TOKEN, result.newAccessToken());
        }
        if (result.newRefreshToken() != null) {
            headers.add(TokenCookieManager.SET_COOKIE, cookies.create(result.newRefreshToken()));
        }
        return headers;
    }
}
