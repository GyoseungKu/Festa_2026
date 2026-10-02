package org.syu_likelion.Festa_2026.poll;

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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.syu_likelion.Festa_2026.auth.AuthorizedSsoExecutor.AuthorizedResult;
import org.syu_likelion.Festa_2026.auth.BearerTokens;
import org.syu_likelion.Festa_2026.auth.TokenCookieManager;
import org.syu_likelion.Festa_2026.poll.PollDtos.PollAdminDetailResponse;
import org.syu_likelion.Festa_2026.poll.PollDtos.PollDetailResponse;
import org.syu_likelion.Festa_2026.poll.PollDtos.PollMutationRequest;
import org.syu_likelion.Festa_2026.poll.PollDtos.PollMediaOrderRequest;
import org.syu_likelion.Festa_2026.poll.PollDtos.PollSettingsRequest;
import org.syu_likelion.Festa_2026.poll.PollDtos.PollSummaryResponse;
import org.syu_likelion.Festa_2026.user.UserController;

@RestController
@RequestMapping("/api/admin/polls")
@Tag(name = "Poll Admin", description = "ADMIN 이상 투표 생성·현황 관리")
@SecurityRequirement(name = "bearerAuth")
public class PollAdminController {
    private final PollAdminApiService admin;
    private final TokenCookieManager cookies;
    public PollAdminController(PollAdminApiService admin, TokenCookieManager cookies) {
        this.admin = admin; this.cookies = cookies;
    }

    @Operation(summary = "관리자 전체 투표 목록 조회", description = "ADMIN 또는 SUPER_ADMIN이 공개 전 투표를 포함한 전체 투표 목록을 조회합니다.")
    @GetMapping ResponseEntity<List<PollSummaryResponse>> list(
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authorization, HttpServletRequest request) {
        return response(admin.list(BearerTokens.require(authorization), cookies.readRefreshToken(request)));
    }

    @PostMapping(value = "/{id}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "투표 기본정보 이미지 업로드·교체", description = "ADMIN 이상. file 파트에 JPG, PNG, WebP 이미지 1개(최대 10MB)를 전달합니다. 응답 시작 후에도 변경할 수 있습니다.")
    ResponseEntity<PollDetailResponse> coverImage(@PathVariable Long id, @RequestPart("file") MultipartFile file,
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authorization,
            HttpServletRequest request) {
        return response(admin.coverImage(id, file, BearerTokens.require(authorization), cookies.readRefreshToken(request)));
    }

    @DeleteMapping("/{id}/image")
    @Operation(summary = "투표 기본정보 이미지 삭제", description = "ADMIN 이상. 이미지가 없는 경우에도 정상 처리합니다.")
    ResponseEntity<PollDetailResponse> deleteCoverImage(@PathVariable Long id,
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authorization,
            HttpServletRequest request) {
        return response(admin.deleteCoverImage(id, BearerTokens.require(authorization), cookies.readRefreshToken(request)));
    }
    @Operation(summary = "관리자 투표 집계와 제출 내역 조회", description = "ADMIN 이상이 실시간 집계와 제출 내역을 조회합니다. page는 0부터, size는 기본 50이며 최대 100입니다. 익명 투표의 제출자 신원은 SUPER_ADMIN에게만 공개됩니다.")
    @GetMapping("/{id}") ResponseEntity<PollAdminDetailResponse> detail(@PathVariable Long id,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size,
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authorization, HttpServletRequest request) {
        return response(admin.detail(id, page, size, BearerTokens.require(authorization), cookies.readRefreshToken(request)));
    }
    @ApiResponse(responseCode = "201", description = "생성 완료", useReturnTypeSchema = true)
    @Operation(summary = "투표 생성", description = "ADMIN 이상이 일정, 참여 정책, 질문과 선택지를 등록합니다. 미디어는 생성 후 별도로 업로드합니다. 성공 시 생성된 투표와 201을 반환합니다.")
    @PostMapping ResponseEntity<PollDetailResponse> create(@Valid @RequestBody PollMutationRequest body,
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authorization, HttpServletRequest request) {
        AuthorizedResult<PollDetailResponse> result = admin.create(body, BearerTokens.require(authorization), cookies.readRefreshToken(request));
        return ResponseEntity.status(HttpStatus.CREATED).headers(headers(result)).body(result.body());
    }
    @Operation(summary = "투표 전체 수정", description = "ADMIN 이상이 질문과 선택지를 포함해 투표를 수정합니다. 제출 내역이 있으면 409 POLL_STRUCTURE_LOCKED를 반환하므로 settings API를 사용해야 합니다.")
    @PutMapping("/{id}") ResponseEntity<PollDetailResponse> update(@PathVariable Long id,
            @Valid @RequestBody PollMutationRequest body,
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authorization, HttpServletRequest request) {
        return response(admin.update(id, body, BearerTokens.require(authorization), cookies.readRefreshToken(request)));
    }
    @Operation(summary = "투표 운영 설정 수정", description = "ADMIN 이상이 제목, 설명, 종료 시각과 결과 공개 시각을 수정합니다. 제출 내역이 생긴 뒤에도 사용할 수 있으며 질문과 선택지는 변경하지 않습니다.")
    @PatchMapping("/{id}/settings") ResponseEntity<PollDetailResponse> settings(@PathVariable Long id,
            @Valid @RequestBody PollSettingsRequest body,
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authorization, HttpServletRequest request) {
        return response(admin.settings(id, body, BearerTokens.require(authorization), cookies.readRefreshToken(request)));
    }
    @Operation(summary = "투표 즉시 종료", description = "ADMIN 이상이 투표를 즉시 종료합니다. 임의 종료는 되돌릴 수 없으며 종료된 투표에는 더 이상 제출할 수 없습니다.")
    @PostMapping("/{id}/close") ResponseEntity<PollDetailResponse> close(@PathVariable Long id,
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authorization, HttpServletRequest request) {
        return response(admin.close(id, BearerTokens.require(authorization), cookies.readRefreshToken(request)));
    }
    @PostMapping(value = "/{id}/options/{optionId}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "투표 선택지 이미지 업로드", description = "ADMIN 이상이 multipart의 file로 선택지 이미지 한 개를 업로드합니다. 기존 이미지는 교체되며 제출 내역이 생긴 뒤에도 변경할 수 있습니다.")
    ResponseEntity<PollDetailResponse> image(@PathVariable Long id, @PathVariable Long optionId,
            @RequestPart("file") MultipartFile file,
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authorization, HttpServletRequest request) {
        return response(admin.image(id, optionId, file, BearerTokens.require(authorization), cookies.readRefreshToken(request)));
    }
    @Operation(summary = "투표 선택지 이미지 삭제", description = "ADMIN 이상이 선택지 이미지와 저장된 파일을 삭제합니다. 제출 내역이 생긴 뒤에도 삭제할 수 있습니다.")
    @DeleteMapping("/{id}/options/{optionId}/image") ResponseEntity<PollDetailResponse> deleteImage(
            @PathVariable Long id, @PathVariable Long optionId,
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authorization, HttpServletRequest request) {
        return response(admin.deleteImage(id, optionId, BearerTokens.require(authorization), cookies.readRefreshToken(request)));
    }
    @PostMapping(value = "/{id}/questions/{questionId}/media", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "투표 질문 미디어 업로드", description = "ADMIN 이상이 multipart의 files로 이미지·동영상을 업로드합니다. 질문당 합계 최대 3개이며 제출 내역이 생긴 뒤에는 변경할 수 없습니다.")
    ResponseEntity<PollDetailResponse> uploadQuestionMedia(@PathVariable Long id, @PathVariable Long questionId,
            @RequestPart("files") List<MultipartFile> files,
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authorization, HttpServletRequest request) {
        return response(admin.uploadQuestionMedia(id, questionId, files, BearerTokens.require(authorization),
                cookies.readRefreshToken(request)));
    }
    @PatchMapping("/{id}/questions/{questionId}/media/order")
    @Operation(summary = "투표 질문 미디어 순서 변경", description = "ADMIN 이상이 현재 질문의 모든 미디어 ID를 중복 없이 mediaIds에 원하는 순서대로 전달합니다. 제출 내역이 있으면 변경할 수 없습니다.")
    ResponseEntity<PollDetailResponse> reorderQuestionMedia(@PathVariable Long id, @PathVariable Long questionId,
            @Valid @RequestBody PollMediaOrderRequest body,
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authorization, HttpServletRequest request) {
        return response(admin.reorderQuestionMedia(id, questionId, body, BearerTokens.require(authorization),
                cookies.readRefreshToken(request)));
    }
    @DeleteMapping("/{id}/questions/{questionId}/media/{mediaId}")
    @Operation(summary = "투표 질문 미디어 삭제", description = "ADMIN 이상이 해당 질문의 미디어와 저장된 파일을 삭제합니다. 제출 내역이 있으면 변경할 수 없습니다.")
    ResponseEntity<PollDetailResponse> removeQuestionMedia(@PathVariable Long id, @PathVariable Long questionId,
            @PathVariable Long mediaId,
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authorization, HttpServletRequest request) {
        return response(admin.removeQuestionMedia(id, questionId, mediaId, BearerTokens.require(authorization),
                cookies.readRefreshToken(request)));
    }
    @ApiResponse(responseCode = "204", description = "처리 완료, 응답 본문 없음", content = @Content)
    @Operation(summary = "투표 삭제", description = "ADMIN 이상이 제출 내역 없는 투표를 삭제합니다. 제출 내역까지 삭제하려면 SUPER_ADMIN 권한과 force=true가 필요합니다. 성공 시 응답 본문 없이 204를 반환합니다.")
    @DeleteMapping("/{id}") ResponseEntity<Void> delete(@PathVariable Long id,
            @RequestParam(defaultValue = "false") boolean force,
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authorization, HttpServletRequest request) {
        AuthorizedResult<Void> result = admin.delete(id, force, BearerTokens.require(authorization), cookies.readRefreshToken(request));
        return ResponseEntity.noContent().headers(headers(result)).build();
    }

    private <T> ResponseEntity<T> response(AuthorizedResult<T> result) { return ResponseEntity.ok().headers(headers(result)).body(result.body()); }
    private HttpHeaders headers(AuthorizedResult<?> result) {
        HttpHeaders headers = new HttpHeaders();
        if (result.newAccessToken() != null) headers.add(UserController.REFRESHED_ACCESS_TOKEN, result.newAccessToken());
        if (result.newRefreshToken() != null) headers.add(TokenCookieManager.SET_COOKIE, cookies.create(result.newRefreshToken()));
        return headers;
    }
}
