package org.syu_likelion.Festa_2026.analytics;

import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.syu_likelion.Festa_2026.analytics.FrontendAnalyticsDtos.EventBatchRequest;
import org.syu_likelion.Festa_2026.analytics.FrontendAnalyticsDtos.EventBatchResponse;
import org.syu_likelion.Festa_2026.analytics.FrontendAnalyticsDtos.FrontendEventRequest;
import org.syu_likelion.Festa_2026.auth.AuthorizedSsoExecutor.AuthorizedResult;
import org.syu_likelion.Festa_2026.error.ApiException;
import org.syu_likelion.Festa_2026.logging.ApiRequestContext;
import org.syu_likelion.Festa_2026.user.UserDtos.MeResponse;
import org.syu_likelion.Festa_2026.user.UserService;

@Service
public class FrontendAnalyticsService {
    private static final Pattern TARGET_ID_PATTERN = Pattern.compile("[A-Za-z0-9_-]{1,100}");
    private static final Pattern ROUTE_PATTERN = Pattern.compile("/[A-Za-z0-9_:/.-]*");
    private final FrontendAnalyticsProperties properties;
    private final AsyncFrontendEventWriter writer;
    private final UserService users;
    private final Clock clock;
    private final ConcurrentHashMap<UUID, SessionRateWindow> sessionRates = new ConcurrentHashMap<>();
    private final AtomicLong nextRateCleanupMillis = new AtomicLong();

    public FrontendAnalyticsService(FrontendAnalyticsProperties properties,
                                    AsyncFrontendEventWriter writer, UserService users, Clock clock) {
        this.properties = properties;
        this.writer = writer;
        this.users = users;
        this.clock = clock;
    }

    public AuthorizedResult<EventBatchResponse> ingest(String accessToken, String refreshToken,
                                                       EventBatchRequest request) {
        validateBatch(request);
        Instant receivedAt = Instant.now(clock);
        for (FrontendEventRequest event : request.events()) validateEvent(event, receivedAt);
        enforceRateLimit(request.sessionId(), request.events().size(), receivedAt);
        AuthorizedResult<MeResponse> authenticated = accessToken == null
                ? null : users.getMe(accessToken, refreshToken);
        UUID userUuid = authenticated == null ? null : authenticated.body().userUuid();
        String requestId = ApiRequestContext.currentRequestId().orElseGet(() -> UUID.randomUUID().toString());
        String appVersion = optionalText(request.appVersion());
        int accepted = 0;
        for (FrontendEventRequest event : request.events()) {
            FrontendEventRecord record = new FrontendEventRecord(event.eventId(), requestId,
                    userUuid, request.sessionId(), event.type(), event.route().trim(),
                    optionalText(event.targetId()), event.durationMs(), event.occurredAt(), receivedAt, appVersion);
            if (writer.publish(record)) accepted++;
        }
        return new AuthorizedResult<>(new EventBatchResponse(accepted),
                authenticated == null ? null : authenticated.newAccessToken(),
                authenticated == null ? null : authenticated.newRefreshToken());
    }

    private void enforceRateLimit(UUID sessionId, int eventCount, Instant now) {
        cleanupRateWindows(now.toEpochMilli());
        long minute = now.getEpochSecond() / 60;
        AtomicBoolean allowed = new AtomicBoolean(true);
        sessionRates.compute(sessionId, (ignored, current) -> {
            if (current == null && sessionRates.size() >= properties.maxTrackedSessions()) {
                allowed.set(false);
                return null;
            }
            int existing = current == null || current.minute() != minute ? 0 : current.count();
            if (existing + eventCount > properties.maxEventsPerMinute()) {
                allowed.set(false);
                return current;
            }
            return new SessionRateWindow(minute, existing + eventCount, now.toEpochMilli());
        });
        if (!allowed.get()) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "FRONTEND_ANALYTICS_RATE_LIMIT",
                    "프런트 이벤트 전송이 너무 많습니다. 잠시 후 다시 시도해 주세요.");
        }
    }

    private void cleanupRateWindows(long nowMillis) {
        long scheduled = nextRateCleanupMillis.get();
        if (nowMillis < scheduled || !nextRateCleanupMillis.compareAndSet(scheduled, nowMillis + 60_000)) return;
        long cutoff = nowMillis - properties.sessionTrackingTtl().toMillis();
        sessionRates.entrySet().removeIf(entry -> entry.getValue().lastSeenMillis() < cutoff);
    }

    private void validateBatch(EventBatchRequest request) {
        if (request == null || request.sessionId() == null || request.events() == null
                || request.events().isEmpty() || request.events().size() > 20) {
            throw invalid("이벤트는 한 번에 1개 이상 20개 이하로 전송해 주세요.");
        }
        Set<UUID> eventIds = new HashSet<>();
        for (FrontendEventRequest event : request.events()) {
            if (event == null || event.eventId() == null || !eventIds.add(event.eventId())) {
                throw invalid("eventId는 요청 안에서 중복될 수 없습니다.");
            }
        }
        if (request.appVersion() != null && request.appVersion().trim().length() > 50) {
            throw invalid("appVersion은 최대 50자입니다.");
        }
    }

    private void validateEvent(FrontendEventRequest event, Instant receivedAt) {
        if (event.type() == null || event.occurredAt() == null) throw invalid("필수 이벤트 정보가 없습니다.");
        String route = event.route() == null ? "" : event.route().trim();
        if (route.isEmpty() || route.length() > 200 || !route.startsWith("/")
                || route.contains("?") || route.contains("#") || containsControl(route)
                || !ROUTE_PATTERN.matcher(route).matches()) {
            throw invalid("route는 쿼리 문자열과 fragment가 없는 정규화된 경로여야 합니다.");
        }
        if (event.occurredAt().isBefore(receivedAt.minus(properties.maximumPastAge()))
                || event.occurredAt().isAfter(receivedAt.plus(properties.maximumFutureSkew()))) {
            throw invalid("이벤트 발생 시각이 허용 범위를 벗어났습니다.");
        }
        if (event.durationMs() != null && (event.type() != FrontendEventType.PAGE_LEAVE
                || event.durationMs() < 0 || event.durationMs() > properties.maximumDuration().toMillis())) {
            throw invalid("durationMs가 허용 범위를 벗어났습니다.");
        }
        if (event.targetId() != null && (event.targetId().isBlank()
                || !TARGET_ID_PATTERN.matcher(event.targetId()).matches())) {
            throw invalid("targetId 형식이 올바르지 않습니다.");
        }
        boolean targetRequired = switch (event.type()) {
            case PERFORMANCE_DETAIL_VIEW, BOOTH_DETAIL_VIEW, NOTICE_DETAIL_VIEW, EXTERNAL_LINK_CLICK -> true;
            default -> false;
        };
        if (targetRequired != (event.targetId() != null)) {
            throw invalid(targetRequired
                    ? "이 이벤트에는 targetId가 필요합니다."
                    : "이 이벤트에는 targetId를 사용할 수 없습니다.");
        }
    }

    private boolean containsControl(String value) {
        return value.chars().anyMatch(character -> character < 0x20 || character == 0x7f);
    }

    private String optionalText(String value) {
        if (value == null || value.isBlank()) return null;
        String trimmed = value.trim();
        if (containsControl(trimmed)) throw invalid("제어 문자는 저장할 수 없습니다.");
        return trimmed;
    }

    private ApiException invalid(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_FRONTEND_EVENT", message);
    }

    private record SessionRateWindow(long minute, int count, long lastSeenMillis) { }
}
