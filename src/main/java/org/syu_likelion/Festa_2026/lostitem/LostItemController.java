package org.syu_likelion.Festa_2026.lostitem;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
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
import org.syu_likelion.Festa_2026.lostitem.LostItemDtos.LostItemMutationRequest;
import org.syu_likelion.Festa_2026.lostitem.LostItemDtos.LostItemPageResponse;
import org.syu_likelion.Festa_2026.lostitem.LostItemDtos.LostItemPinUpdateRequest;
import org.syu_likelion.Festa_2026.lostitem.LostItemDtos.LostItemResponse;
import org.syu_likelion.Festa_2026.lostitem.LostItemDtos.LostItemStatusUpdateRequest;
import org.syu_likelion.Festa_2026.user.UserController;

@RestController
@RequestMapping("/api/lost-items")
@Tag(name = "Lost Item", description = "로그인 없이 조회할 수 있는 분실물 공지")
public class LostItemController {
    private final LostItemService lostItems;
    private final LostItemApiService api;
    private final TokenCookieManager cookies;

    public LostItemController(LostItemService lostItems, LostItemApiService api, TokenCookieManager cookies) {
        this.lostItems = lostItems;
        this.api = api;
        this.cookies = cookies;
    }

    @GetMapping
    @Operation(summary = "분실물 공지 목록 조회",
            description = "로그인 없이 분실물 공지를 페이지 단위로 조회합니다. status로 반환 상태를 필터링하고 sort로 정렬 기준을 선택합니다. page는 0부터 시작합니다.")
    ResponseEntity<LostItemPageResponse> list(
            @RequestParam(required = false) LostItemStatus status,
            @RequestParam(defaultValue = "NEWEST") LostItemSort sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(lostItems.listPublic(status, sort, page, size));
    }

    @GetMapping("/{id}")
    @Operation(summary = "분실물 공지 상세 조회", description = "호출할 때마다 조회수가 1 증가합니다.")
    ResponseEntity<LostItemResponse> detail(@PathVariable Long id) {
        return ResponseEntity.ok(lostItems.getPublic(id));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ApiResponse(responseCode = "201", description = "생성 완료", useReturnTypeSchema = true)
    @Operation(summary = "분실물 공지 등록", description = "STAFF 이상이 공지와 사진을 등록합니다.")
    @SecurityRequirement(name = "bearerAuth")
    ResponseEntity<LostItemResponse> create(
            @Valid @RequestPart("data") LostItemMutationRequest data,
            @RequestPart(value = "images", required = false) List<MultipartFile> images,
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authorization,
            @Parameter(hidden = true) HttpServletRequest request) {
        AuthorizedResult<LostItemResponse> result = api.create(BearerTokens.require(authorization),
                cookies.readRefreshToken(request), data, images);
        return ResponseEntity.status(HttpStatus.CREATED).headers(headers(result)).body(result.body());
    }

    @PatchMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "분실물 공지 수정", description = "STAFF 이상이 내용과 사진 구성을 수정합니다.")
    @SecurityRequirement(name = "bearerAuth")
    ResponseEntity<LostItemResponse> update(
            @PathVariable Long id,
            @Valid @RequestPart("data") LostItemMutationRequest data,
            @RequestParam(value = "removeImageIds", required = false) List<Long> removeImageIds,
            @RequestPart(value = "images", required = false) List<MultipartFile> images,
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authorization,
            @Parameter(hidden = true) HttpServletRequest request) {
        return response(api.update(id, BearerTokens.require(authorization), cookies.readRefreshToken(request),
                data, removeImageIds, images));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "분실물 반환 상태 변경",
            description = "STAFF 이상이 분실물의 반환 상태를 요청 본문의 status 값으로 변경하고 수정된 공지를 반환합니다.")
    @SecurityRequirement(name = "bearerAuth")
    ResponseEntity<LostItemResponse> changeStatus(
            @PathVariable Long id, @Valid @RequestBody LostItemStatusUpdateRequest body,
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authorization,
            @Parameter(hidden = true) HttpServletRequest request) {
        return response(api.changeStatus(id, BearerTokens.require(authorization),
                cookies.readRefreshToken(request), body.status()));
    }

    @PatchMapping("/{id}/pin")
    @Operation(summary = "분실물 공지 상단 고정 변경",
            description = "STAFF 이상이 요청 본문의 pinned 값으로 공지의 상단 고정을 설정하거나 해제합니다.")
    @SecurityRequirement(name = "bearerAuth")
    ResponseEntity<LostItemResponse> changePin(
            @PathVariable Long id, @Valid @RequestBody LostItemPinUpdateRequest body,
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authorization,
            @Parameter(hidden = true) HttpServletRequest request) {
        return response(api.changePinned(id, BearerTokens.require(authorization),
                cookies.readRefreshToken(request), body.pinned()));
    }

    @DeleteMapping("/{id}")
    @ApiResponse(responseCode = "204", description = "처리 완료, 응답 본문 없음", content = @Content)
    @Operation(summary = "분실물 공지 삭제", description = "공지와 업로드된 사진을 함께 삭제합니다.")
    @SecurityRequirement(name = "bearerAuth")
    ResponseEntity<Void> delete(
            @PathVariable Long id,
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authorization,
            @Parameter(hidden = true) HttpServletRequest request) {
        AuthorizedResult<Void> result = api.delete(id, BearerTokens.require(authorization),
                cookies.readRefreshToken(request));
        return ResponseEntity.noContent().headers(headers(result)).build();
    }

    private ResponseEntity<LostItemResponse> response(AuthorizedResult<LostItemResponse> result) {
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
