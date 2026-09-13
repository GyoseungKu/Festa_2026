package org.syu_likelion.Festa_2026.poll;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.syu_likelion.Festa_2026.auth.AuthorizedSsoExecutor.AuthorizedResult;
import org.syu_likelion.Festa_2026.auth.BearerTokens;
import org.syu_likelion.Festa_2026.auth.TokenCookieManager;
import org.syu_likelion.Festa_2026.poll.PollDtos.MySubmissionResponse;
import org.syu_likelion.Festa_2026.poll.PollDtos.PollDetailResponse;
import org.syu_likelion.Festa_2026.poll.PollDtos.PollResultResponse;
import org.syu_likelion.Festa_2026.poll.PollDtos.PollSubmissionRequest;
import org.syu_likelion.Festa_2026.poll.PollDtos.PollSummaryResponse;
import org.syu_likelion.Festa_2026.poll.PollDtos.SubmissionReceipt;
import org.syu_likelion.Festa_2026.user.UserController;

@RestController
@RequestMapping("/api/polls")
@Tag(name = "Poll", description = "로그인 사용자 투표·응답 폼")
@SecurityRequirement(name = "bearerAuth")
public class PollController {
    private final PollService polls;
    private final TokenCookieManager cookies;

    public PollController(PollService polls, TokenCookieManager cookies) {
        this.polls = polls; this.cookies = cookies;
    }

    @GetMapping
    @Operation(summary = "공개된 투표 목록", description = "진행 중, 진행 예정, 종료 순으로 조회합니다.")
    ResponseEntity<List<PollSummaryResponse>> list(
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authorization,
            @Parameter(hidden = true) HttpServletRequest request) {
        return response(polls.listVisible(BearerTokens.require(authorization), cookies.readRefreshToken(request)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "투표 상세",
            description = "로그인한 사용자가 공개된 투표의 질문·선택지·미디어와 내 참여 상태를 조회합니다. 공개 시각 전에는 404를 반환합니다.")
    ResponseEntity<PollDetailResponse> detail(@PathVariable Long id,
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authorization,
            @Parameter(hidden = true) HttpServletRequest request) {
        return response(polls.getVisible(id, BearerTokens.require(authorization), cookies.readRefreshToken(request)));
    }

    @PostMapping("/{id}/submissions")
    @ApiResponse(responseCode = "201", description = "생성 완료", useReturnTypeSchema = true)
    @Operation(summary = "투표 제출",
            description = "로그인한 사용자가 진행 중인 투표에 답변을 제출합니다. 필수 질문과 질문 유형별 답변 형식을 검증합니다. 중복 참여를 허용하지 않는 투표는 사용자당 한 번만 제출할 수 있으며 성공 시 201을 반환합니다.")
    ResponseEntity<SubmissionReceipt> submit(@PathVariable Long id, @Valid @RequestBody PollSubmissionRequest body,
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authorization,
            @Parameter(hidden = true) HttpServletRequest request) {
        AuthorizedResult<SubmissionReceipt> result = polls.submit(id, body, BearerTokens.require(authorization),
                cookies.readRefreshToken(request));
        return ResponseEntity.status(HttpStatus.CREATED).headers(headers(result)).body(result.body());
    }

    @GetMapping("/{id}/submissions/me")
    @Operation(summary = "내 제출 내역",
            description = "로그인한 사용자의 해당 투표 제출 내역을 최신순으로 반환합니다. 익명 투표에서도 자신의 답변은 조회할 수 있습니다.")
    ResponseEntity<List<MySubmissionResponse>> mine(@PathVariable Long id,
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authorization,
            @Parameter(hidden = true) HttpServletRequest request) {
        return response(polls.mySubmissions(id, BearerTokens.require(authorization), cookies.readRefreshToken(request)));
    }

    @GetMapping("/{id}/results")
    @Operation(summary = "사용자 공개 결과", description = "관리자가 정한 결과 공개 시각 이후에만 조회할 수 있습니다.")
    ResponseEntity<PollResultResponse> results(@PathVariable Long id,
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authorization,
            @Parameter(hidden = true) HttpServletRequest request) {
        return response(polls.publicResults(id, BearerTokens.require(authorization), cookies.readRefreshToken(request)));
    }

    private <T> ResponseEntity<T> response(AuthorizedResult<T> result) {
        return ResponseEntity.ok().headers(headers(result)).body(result.body());
    }
    private HttpHeaders headers(AuthorizedResult<?> result) {
        HttpHeaders headers = new HttpHeaders();
        if (result.newAccessToken() != null) headers.add(UserController.REFRESHED_ACCESS_TOKEN, result.newAccessToken());
        if (result.newRefreshToken() != null) headers.add(TokenCookieManager.SET_COOKIE, cookies.create(result.newRefreshToken()));
        return headers;
    }
}
