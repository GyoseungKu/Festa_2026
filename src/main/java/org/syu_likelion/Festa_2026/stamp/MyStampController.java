package org.syu_likelion.Festa_2026.stamp;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.syu_likelion.Festa_2026.auth.AuthorizedSsoExecutor.AuthorizedResult;
import org.syu_likelion.Festa_2026.auth.BearerTokens;
import org.syu_likelion.Festa_2026.auth.TokenCookieManager;
import org.syu_likelion.Festa_2026.stamp.StampDtos.MyStampBoardResponse;
import org.syu_likelion.Festa_2026.user.UserController;

@RestController
@RequestMapping("/api/users/me/stamps")
@Tag(name = "Stamp", description = "내 스탬프판")
@SecurityRequirement(name = "bearerAuth")
public class MyStampController {
    private final StampService stamps;
    private final TokenCookieManager cookies;
    public MyStampController(StampService stamps, TokenCookieManager cookies) { this.stamps = stamps; this.cookies = cookies; }
    @GetMapping
    @Operation(summary = "내 스탬프 현황 조회", description = "스탬프판은 축제 기간 중 한 번만 참여하며 회차나 초기화 개념이 없습니다.")
    ResponseEntity<MyStampBoardResponse> get(
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authorization,
            @Parameter(hidden = true) HttpServletRequest request) {
        AuthorizedResult<MyStampBoardResponse> result = stamps.myBoard(
                BearerTokens.require(authorization), cookies.readRefreshToken(request));
        ResponseEntity.BodyBuilder response = ResponseEntity.ok();
        if (result.newAccessToken() != null) response.header(UserController.REFRESHED_ACCESS_TOKEN, result.newAccessToken());
        if (result.newRefreshToken() != null) response.header(TokenCookieManager.SET_COOKIE, cookies.create(result.newRefreshToken()));
        return response.body(result.body());
    }
}
