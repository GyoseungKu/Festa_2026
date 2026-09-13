package org.syu_likelion.Festa_2026.analytics;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.syu_likelion.Festa_2026.analytics.FrontendAnalyticsDtos.EventBatchRequest;
import org.syu_likelion.Festa_2026.analytics.FrontendAnalyticsDtos.EventBatchResponse;
import org.syu_likelion.Festa_2026.auth.AuthorizedSsoExecutor.AuthorizedResult;
import org.syu_likelion.Festa_2026.auth.BearerTokens;
import org.syu_likelion.Festa_2026.auth.TokenCookieManager;
import org.syu_likelion.Festa_2026.user.UserController;

@RestController
@RequestMapping("/api/analytics")
@Tag(name = "Frontend Analytics", description = "React 페이지 방문 및 주요 사용자 행동 이벤트 수집")
public class FrontendAnalyticsController {
    private final FrontendAnalyticsService analytics;
    private final TokenCookieManager cookies;

    public FrontendAnalyticsController(FrontendAnalyticsService analytics, TokenCookieManager cookies) {
        this.analytics = analytics;
        this.cookies = cookies;
    }

    @PostMapping("/events")
    @ApiResponse(responseCode = "202", description = "이벤트 수집 요청 접수", useReturnTypeSchema = true)
    @Operation(summary = "프론트 이벤트 일괄 수집",
            description = "로그인 여부와 관계없이 React 페이지 방문 및 주요 행동 이벤트를 최대 20개까지 비동기로 수집합니다. Bearer Token은 선택이며, 제공하면 검증된 userUuid가 연결됩니다. 개인정보, 전체 URL 및 쿼리 문자열은 요청으로 받지 않습니다.")
    ResponseEntity<EventBatchResponse> events(
            @Valid @RequestBody EventBatchRequest body,
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authorization,
            @Parameter(hidden = true) HttpServletRequest request) {
        String accessToken = authorization == null || authorization.isBlank()
                ? null : BearerTokens.require(authorization);
        AuthorizedResult<EventBatchResponse> result = analytics.ingest(accessToken,
                cookies.readRefreshToken(request), body);
        HttpHeaders headers = new HttpHeaders();
        if (result.newAccessToken() != null) {
            headers.add(UserController.REFRESHED_ACCESS_TOKEN, result.newAccessToken());
        }
        if (result.newRefreshToken() != null) {
            headers.add(TokenCookieManager.SET_COOKIE, cookies.create(result.newRefreshToken()));
        }
        return ResponseEntity.status(HttpStatus.ACCEPTED).headers(headers).body(result.body());
    }
}
