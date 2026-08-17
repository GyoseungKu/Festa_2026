package org.syu_likelion.Festa_2026.booth;

import io.swagger.v3.oas.annotations.Operation;
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
import org.syu_likelion.Festa_2026.booth.BoothDtos.BoothAdminResponse;
import org.syu_likelion.Festa_2026.booth.BoothDtos.BoothDetailResponse;
import org.syu_likelion.Festa_2026.booth.BoothDtos.BoothMediaOrderRequest;
import org.syu_likelion.Festa_2026.booth.BoothDtos.BoothMutationRequest;
import org.syu_likelion.Festa_2026.booth.BoothDtos.BoothSummaryResponse;
import org.syu_likelion.Festa_2026.user.UserController;

@RestController
@RequestMapping("/api/booths")
@Tag(name = "Booth", description = "축제 부스 지도, 상세 정보, 찜 및 관리자 관리")
public class BoothController {
    private final BoothService booths;
    private final TokenCookieManager cookies;
    public BoothController(BoothService booths, TokenCookieManager cookies) { this.booths = booths; this.cookies = cookies; }

    @GetMapping
    @Operation(summary = "전체 부스 핀 조회", description = "로그인 없이 지도에 표시할 모든 부스를 조회합니다. 로그인하면 각 부스의 내 찜 여부도 반환합니다.")
    ResponseEntity<List<BoothSummaryResponse>> list(
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authorization,
            @Parameter(hidden = true) HttpServletRequest request) {
        return ok(booths.list(optionalBearer(authorization), cookies.readRefreshToken(request)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "부스 상세 조회", description = "로그인 없이 부스 설명과 정렬된 이미지·동영상을 조회합니다.")
    ResponseEntity<BoothDetailResponse> detail(@PathVariable Long id,
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authorization,
            @Parameter(hidden = true) HttpServletRequest request) {
        return ok(booths.detail(id, optionalBearer(authorization), cookies.readRefreshToken(request)));
    }

    @PostMapping("/{id}/favorite")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "부스 찜 등록")
    ResponseEntity<Void> favorite(@PathVariable Long id,
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authorization,
            @Parameter(hidden = true) HttpServletRequest request) {
        return noContent(booths.favorite(id, BearerTokens.require(authorization), cookies.readRefreshToken(request)));
    }

    @DeleteMapping("/{id}/favorite")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "부스 찜 해제")
    ResponseEntity<Void> unfavorite(@PathVariable Long id,
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authorization,
            @Parameter(hidden = true) HttpServletRequest request) {
        return noContent(booths.unfavorite(id, BearerTokens.require(authorization), cookies.readRefreshToken(request)));
    }

    @PostMapping
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "부스 등록", description = "ADMIN 또는 SUPER_ADMIN이 기본 정보를 등록합니다. 미디어는 등록 후 업로드합니다.")
    ResponseEntity<BoothAdminResponse> create(@Valid @RequestBody BoothMutationRequest body,
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authorization,
            @Parameter(hidden = true) HttpServletRequest request) {
        AuthorizedResult<BoothAdminResponse> result = booths.create(BearerTokens.require(authorization),
                cookies.readRefreshToken(request), body);
        return ResponseEntity.status(HttpStatus.CREATED).headers(headers(result)).body(result.body());
    }

    @PatchMapping("/{id}")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "부스 기본 정보 수정", description = "ADMIN 또는 SUPER_ADMIN만 수정할 수 있습니다.")
    ResponseEntity<BoothAdminResponse> update(@PathVariable Long id, @Valid @RequestBody BoothMutationRequest body,
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authorization,
            @Parameter(hidden = true) HttpServletRequest request) {
        return ok(booths.update(id, BearerTokens.require(authorization), cookies.readRefreshToken(request), body));
    }

    @PostMapping(value = "/{id}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "부스 이미지 업로드", description = "JPG, PNG, WebP, GIF 파일을 최대 5개까지 등록합니다.")
    ResponseEntity<BoothAdminResponse> uploadImages(@PathVariable Long id, @RequestPart("files") List<MultipartFile> files,
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authorization,
            @Parameter(hidden = true) HttpServletRequest request) {
        return ok(booths.upload(id, BoothMediaKind.IMAGE, files, BearerTokens.require(authorization), cookies.readRefreshToken(request)));
    }

    @PostMapping(value = "/{id}/videos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "부스 동영상 업로드", description = "MP4, WebM, MOV 파일을 최대 3개까지 등록합니다.")
    ResponseEntity<BoothAdminResponse> uploadVideos(@PathVariable Long id, @RequestPart("files") List<MultipartFile> files,
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authorization,
            @Parameter(hidden = true) HttpServletRequest request) {
        return ok(booths.upload(id, BoothMediaKind.VIDEO, files, BearerTokens.require(authorization), cookies.readRefreshToken(request)));
    }

    @PatchMapping("/{id}/media/order")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "통합 미디어 순서와 대표 미디어 설정")
    ResponseEntity<BoothAdminResponse> orderMedia(@PathVariable Long id, @Valid @RequestBody BoothMediaOrderRequest body,
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authorization,
            @Parameter(hidden = true) HttpServletRequest request) {
        return ok(booths.orderMedia(id, body, BearerTokens.require(authorization), cookies.readRefreshToken(request)));
    }

    @DeleteMapping("/{id}/media/{mediaId}")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "부스 미디어 삭제")
    ResponseEntity<BoothAdminResponse> deleteMedia(@PathVariable Long id, @PathVariable Long mediaId,
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authorization,
            @Parameter(hidden = true) HttpServletRequest request) {
        return ok(booths.deleteMedia(id, mediaId, BearerTokens.require(authorization), cookies.readRefreshToken(request)));
    }

    @DeleteMapping("/{id}")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "부스 삭제", description = "ADMIN 또는 SUPER_ADMIN이 찜과 업로드 미디어를 함께 삭제합니다.")
    ResponseEntity<Void> delete(@PathVariable Long id,
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authorization,
            @Parameter(hidden = true) HttpServletRequest request) {
        return noContent(booths.delete(id, BearerTokens.require(authorization), cookies.readRefreshToken(request)));
    }

    private String optionalBearer(String authorization) {
        return authorization == null || authorization.isBlank() ? null : BearerTokens.require(authorization);
    }
    private <T> ResponseEntity<T> ok(AuthorizedResult<T> result) {
        return ResponseEntity.ok().headers(headers(result)).body(result.body());
    }
    private ResponseEntity<Void> noContent(AuthorizedResult<Void> result) {
        return ResponseEntity.noContent().headers(headers(result)).build();
    }
    private org.springframework.http.HttpHeaders headers(AuthorizedResult<?> result) {
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        if (result.newAccessToken() != null) headers.add(UserController.REFRESHED_ACCESS_TOKEN, result.newAccessToken());
        if (result.newRefreshToken() != null) headers.add(TokenCookieManager.SET_COOKIE, cookies.create(result.newRefreshToken()));
        return headers;
    }
}
