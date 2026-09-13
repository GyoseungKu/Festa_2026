package org.syu_likelion.Festa_2026.sponsor;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.syu_likelion.Festa_2026.auth.*;
import org.syu_likelion.Festa_2026.error.ApiException;
import org.syu_likelion.Festa_2026.user.*;
import org.syu_likelion.Festa_2026.sponsor.SponsorService.SponsorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/sponsors")
@Tag(name = "Sponsor", description = "협찬사 공개 조회 및 ADMIN 이상 협찬사 관리")
public class SponsorController {
    private final SponsorService sponsors;
    private final UserService users;
    private final TokenCookieManager cookies;
    public SponsorController(SponsorService sponsors, UserService users, TokenCookieManager cookies) {
        this.sponsors = sponsors; this.users = users; this.cookies = cookies;
    }
    @GetMapping
    @Operation(summary = "협찬사 전체 조회", description = "비로그인 조회 가능. 등록 순서로 반환합니다.")
    public List<SponsorResponse> list() { return sponsors.list(); }
    @GetMapping("/{id}")
    @Operation(summary = "협찬사 상세 조회", description = "로그인 없이 협찬사 이름, 설명, 이미지와 연결된 부스 정보를 조회합니다.")
    public SponsorResponse get(@PathVariable Long id) { return sponsors.get(id); }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponse(responseCode = "201", description = "생성 완료", useReturnTypeSchema = true)
    @Operation(summary = "협찬사 등록", description = "ADMIN 이상. name, description, image 필수. boothId 선택.")
    public ResponseEntity<SponsorResponse> create(@RequestParam String name, @RequestParam String description,
            @RequestParam(required = false) Long boothId, @RequestParam MultipartFile image,
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authorization, HttpServletRequest request) {
        var auth = authenticate(authorization, request);
        return ResponseEntity.status(201).headers(headers(auth)).body(
                sponsors.save(null, auth.body().userUuid(), name, description, boothId, image));
    }
    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "협찬사 수정", description = "ADMIN 이상. 사진 생략 시 유지, boothId 생략 시 부스 연결 해제.")
    public ResponseEntity<SponsorResponse> update(@PathVariable Long id, @RequestParam String name,
            @RequestParam String description, @RequestParam(required = false) Long boothId,
            @RequestParam(required = false) MultipartFile image,
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authorization, HttpServletRequest request) {
        var auth = authenticate(authorization, request);
        return ResponseEntity.ok().headers(headers(auth)).body(
                sponsors.save(id, auth.body().userUuid(), name, description, boothId, image));
    }
    @DeleteMapping("/{id}")
    @ApiResponse(responseCode = "204", description = "처리 완료, 응답 본문 없음", content = @Content)
    @Operation(summary = "협찬사 삭제", description = "ADMIN 또는 SUPER_ADMIN이 협찬사와 저장된 이미지를 삭제합니다. 성공 시 응답 본문 없이 204를 반환합니다.")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Void> delete(@PathVariable Long id,
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authorization, HttpServletRequest request) {
        var auth = authenticate(authorization, request);
        sponsors.delete(id);
        return ResponseEntity.noContent().headers(headers(auth)).build();
    }
    private AuthorizedSsoExecutor.AuthorizedResult<UserDtos.MeResponse> authenticate(String authorization, HttpServletRequest request) {
        var auth = users.getMe(BearerTokens.require(authorization), cookies.readRefreshToken(request));
        var roles = auth.body().festivalRoles();
        if (roles == null || (!roles.contains(FestivalRole.ADMIN) && !roles.contains(FestivalRole.SUPER_ADMIN)))
            throw new ApiException(HttpStatus.FORBIDDEN, "SPONSOR_MANAGE_FORBIDDEN", "협찬사 관리는 ADMIN 이상만 가능합니다.");
        return auth;
    }
    private HttpHeaders headers(AuthorizedSsoExecutor.AuthorizedResult<?> auth) {
        HttpHeaders headers = new HttpHeaders();
        if (auth.newAccessToken() != null) headers.add(UserController.REFRESHED_ACCESS_TOKEN, auth.newAccessToken());
        if (auth.newRefreshToken() != null) headers.add(TokenCookieManager.SET_COOKIE, cookies.create(auth.newRefreshToken()));
        return headers;
    }
}

