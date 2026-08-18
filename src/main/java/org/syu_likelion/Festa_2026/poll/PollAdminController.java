package org.syu_likelion.Festa_2026.poll;

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

    @GetMapping ResponseEntity<List<PollSummaryResponse>> list(
            @RequestHeader(value = "Authorization", required = false) String authorization, HttpServletRequest request) {
        return response(admin.list(BearerTokens.require(authorization), cookies.readRefreshToken(request)));
    }
    @GetMapping("/{id}") ResponseEntity<PollAdminDetailResponse> detail(@PathVariable Long id,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size,
            @RequestHeader(value = "Authorization", required = false) String authorization, HttpServletRequest request) {
        return response(admin.detail(id, page, size, BearerTokens.require(authorization), cookies.readRefreshToken(request)));
    }
    @PostMapping ResponseEntity<PollDetailResponse> create(@Valid @RequestBody PollMutationRequest body,
            @RequestHeader(value = "Authorization", required = false) String authorization, HttpServletRequest request) {
        AuthorizedResult<PollDetailResponse> result = admin.create(body, BearerTokens.require(authorization), cookies.readRefreshToken(request));
        return ResponseEntity.status(HttpStatus.CREATED).headers(headers(result)).body(result.body());
    }
    @PutMapping("/{id}") ResponseEntity<PollDetailResponse> update(@PathVariable Long id,
            @Valid @RequestBody PollMutationRequest body,
            @RequestHeader(value = "Authorization", required = false) String authorization, HttpServletRequest request) {
        return response(admin.update(id, body, BearerTokens.require(authorization), cookies.readRefreshToken(request)));
    }
    @PatchMapping("/{id}/settings") ResponseEntity<PollDetailResponse> settings(@PathVariable Long id,
            @Valid @RequestBody PollSettingsRequest body,
            @RequestHeader(value = "Authorization", required = false) String authorization, HttpServletRequest request) {
        return response(admin.settings(id, body, BearerTokens.require(authorization), cookies.readRefreshToken(request)));
    }
    @PostMapping("/{id}/close") ResponseEntity<PollDetailResponse> close(@PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String authorization, HttpServletRequest request) {
        return response(admin.close(id, BearerTokens.require(authorization), cookies.readRefreshToken(request)));
    }
    @PostMapping(value = "/{id}/options/{optionId}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<PollDetailResponse> image(@PathVariable Long id, @PathVariable Long optionId,
            @RequestPart("file") MultipartFile file,
            @RequestHeader(value = "Authorization", required = false) String authorization, HttpServletRequest request) {
        return response(admin.image(id, optionId, file, BearerTokens.require(authorization), cookies.readRefreshToken(request)));
    }
    @DeleteMapping("/{id}/options/{optionId}/image") ResponseEntity<PollDetailResponse> deleteImage(
            @PathVariable Long id, @PathVariable Long optionId,
            @RequestHeader(value = "Authorization", required = false) String authorization, HttpServletRequest request) {
        return response(admin.deleteImage(id, optionId, BearerTokens.require(authorization), cookies.readRefreshToken(request)));
    }
    @PostMapping(value = "/{id}/questions/{questionId}/media", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<PollDetailResponse> uploadQuestionMedia(@PathVariable Long id, @PathVariable Long questionId,
            @RequestPart("files") List<MultipartFile> files,
            @RequestHeader(value = "Authorization", required = false) String authorization, HttpServletRequest request) {
        return response(admin.uploadQuestionMedia(id, questionId, files, BearerTokens.require(authorization),
                cookies.readRefreshToken(request)));
    }
    @PatchMapping("/{id}/questions/{questionId}/media/order")
    ResponseEntity<PollDetailResponse> reorderQuestionMedia(@PathVariable Long id, @PathVariable Long questionId,
            @Valid @RequestBody PollMediaOrderRequest body,
            @RequestHeader(value = "Authorization", required = false) String authorization, HttpServletRequest request) {
        return response(admin.reorderQuestionMedia(id, questionId, body, BearerTokens.require(authorization),
                cookies.readRefreshToken(request)));
    }
    @DeleteMapping("/{id}/questions/{questionId}/media/{mediaId}")
    ResponseEntity<PollDetailResponse> removeQuestionMedia(@PathVariable Long id, @PathVariable Long questionId,
            @PathVariable Long mediaId,
            @RequestHeader(value = "Authorization", required = false) String authorization, HttpServletRequest request) {
        return response(admin.removeQuestionMedia(id, questionId, mediaId, BearerTokens.require(authorization),
                cookies.readRefreshToken(request)));
    }
    @DeleteMapping("/{id}") ResponseEntity<Void> delete(@PathVariable Long id,
            @RequestParam(defaultValue = "false") boolean force,
            @RequestHeader(value = "Authorization", required = false) String authorization, HttpServletRequest request) {
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
