package org.syu_likelion.Festa_2026.analytics;

import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class FrontendAnalyticsDtos {
    private FrontendAnalyticsDtos() { }

    public record EventBatchRequest(
            @NotNull UUID sessionId,
            @Size(max = 50) String appVersion,
            @NotEmpty @Size(max = 20) List<@Valid FrontendEventRequest> events) { }

    public record FrontendEventRequest(
            @NotNull UUID eventId,
            @NotNull FrontendEventType type,
            @Schema(description = "/로 시작하는 정규화된 경로. 쿼리 문자열·fragment·제어 문자는 허용하지 않습니다.", example = "/booths/1")
            @NotBlank @Size(max = 200) String route,
            @Schema(description = "PERFORMANCE_DETAIL_VIEW·BOOTH_DETAIL_VIEW·NOTICE_DETAIL_VIEW·EXTERNAL_LINK_CLICK에서는 필수. 나머지 이벤트에서는 생략/null이어야 합니다.")
            @Pattern(regexp = "^[A-Za-z0-9_-]{1,100}$") String targetId,
            @Schema(description = "이벤트 발생 시각(UTC ISO-8601). 서버 수신 시각 기준 기본 과거 24시간~미래 5분 이내. 허용 범위는 서버 설정에 따라 달라집니다.")
            @NotNull Instant occurredAt,
            @Schema(description = "PAGE_LEAVE에서만 선택적으로 전달하는 체류 시간(ms). 다른 이벤트에서는 생략/null. 0 이상, 기본 최대 12시간(43200000ms)이며 상한은 서버 설정에 따릅니다.", minimum = "0")
            Long durationMs) { }

    public record EventBatchResponse(int acceptedEvents) { }
}
