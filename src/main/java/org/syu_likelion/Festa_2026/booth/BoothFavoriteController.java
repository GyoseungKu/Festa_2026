package org.syu_likelion.Festa_2026.booth;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.syu_likelion.Festa_2026.auth.AuthorizedSsoExecutor.AuthorizedResult;
import org.syu_likelion.Festa_2026.auth.BearerTokens;
import org.syu_likelion.Festa_2026.auth.TokenCookieManager;
import org.syu_likelion.Festa_2026.booth.BoothDtos.BoothSummaryResponse;
import org.syu_likelion.Festa_2026.user.UserController;

@RestController
@RequestMapping("/api/users/me/favorite-booths")
@Tag(name = "Booth", description = "축제 부스 지도, 상세 정보, 찜 및 관리자 관리")
@SecurityRequirement(name = "bearerAuth")
public class BoothFavoriteController {
    private final BoothService booths;
    private final TokenCookieManager cookies;
    public BoothFavoriteController(BoothService booths, TokenCookieManager cookies) { this.booths = booths; this.cookies = cookies; }
    @GetMapping
    @Operation(summary = "내가 찜한 부스 목록 조회")
    ResponseEntity<List<BoothSummaryResponse>> list(
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authorization,
            @Parameter(hidden = true) HttpServletRequest request) {
        AuthorizedResult<List<BoothSummaryResponse>> result = booths.myFavorites(
                BearerTokens.require(authorization), cookies.readRefreshToken(request));
        ResponseEntity.BodyBuilder response = ResponseEntity.ok();
        if (result.newAccessToken() != null) response.header(UserController.REFRESHED_ACCESS_TOKEN, result.newAccessToken());
        if (result.newRefreshToken() != null) response.header(TokenCookieManager.SET_COOKIE, cookies.create(result.newRefreshToken()));
        return response.body(result.body());
    }
}
