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

@RestController
@RequestMapping("/api/sponsors")
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
    public SponsorResponse get(@PathVariable Long id) { return sponsors.get(id); }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "협찬사 등록", description = "ADMIN 이상. name, description, image 필수. boothId 선택.")
    public ResponseEntity<SponsorResponse> create(@RequestParam String name, @RequestParam String description,
            @RequestParam(required = false) Long boothId, @RequestParam MultipartFile image,
            @RequestHeader(value = "Authorization", required = false) String authorization, HttpServletRequest request) {
        var auth = authenticate(authorization, request);
        return ResponseEntity.status(201).headers(headers(auth)).body(
                sponsors.save(null, auth.body().userUuid(), name, description, boothId, image));
    }
    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "협찬사 수정", description = "ADMIN 이상. 사진 생략 시 유지, boothId 생략 시 부스 연결 해제.")
    public ResponseEntity<SponsorResponse> update(@PathVariable Long id, @RequestParam String name,
            @RequestParam String description, @RequestParam(required = false) Long boothId,
            @RequestParam(required = false) MultipartFile image,
            @RequestHeader(value = "Authorization", required = false) String authorization, HttpServletRequest request) {
        var auth = authenticate(authorization, request);
        return ResponseEntity.ok().headers(headers(auth)).body(
                sponsors.save(id, auth.body().userUuid(), name, description, boothId, image));
    }
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String authorization, HttpServletRequest request) {
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

