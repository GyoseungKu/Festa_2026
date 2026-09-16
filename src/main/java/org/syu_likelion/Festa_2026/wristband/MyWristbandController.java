package org.syu_likelion.Festa_2026.wristband;

import jakarta.servlet.http.HttpServletRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.syu_likelion.Festa_2026.auth.BearerTokens;
import org.syu_likelion.Festa_2026.auth.TokenCookieManager;
import org.syu_likelion.Festa_2026.user.UserService;

@RestController
@Tag(name = "Wristband", description = "축제 무대 입장 팔찌 지급 상태")
@SecurityRequirement(name = "bearerAuth")
public class MyWristbandController {
    private final UserService users;
    private final TokenCookieManager cookies;
    private final WristbandService wristbands;
    public MyWristbandController(UserService users, TokenCookieManager cookies, WristbandService wristbands) {
        this.users = users; this.cookies = cookies; this.wristbands = wristbands;
    }
    @GetMapping("/api/users/me/wristband")
    @Operation(summary = "내 입장 팔찌 지급 여부", description = "본인의 현재 지급 여부와 지급 시각을 조회합니다. 철회 시 issued=false입니다. 새 계정의 과거 지급 연결은 학생 인증 후 확인할 수 있습니다.")
    public ResponseEntity<WristbandService.MyStatus> mine(
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authorization,
            @Parameter(hidden = true) HttpServletRequest request) {
        var auth = users.getMe(BearerTokens.require(authorization), cookies.readRefreshToken(request));
        var response = ResponseEntity.ok().header("Cache-Control", "no-store");
        if (auth.newAccessToken() != null) response.header("X-Access-Token", auth.newAccessToken());
        if (auth.newRefreshToken() != null) response.header(TokenCookieManager.SET_COOKIE, cookies.create(auth.newRefreshToken()));
        return response.body(wristbands.mine(auth.body().userUuid()));
    }
}
