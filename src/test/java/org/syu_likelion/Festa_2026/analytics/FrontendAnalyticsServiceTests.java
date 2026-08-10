package org.syu_likelion.Festa_2026.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.syu_likelion.Festa_2026.analytics.FrontendAnalyticsDtos.EventBatchRequest;
import org.syu_likelion.Festa_2026.analytics.FrontendAnalyticsDtos.FrontendEventRequest;
import org.syu_likelion.Festa_2026.auth.AuthorizedSsoExecutor.AuthorizedResult;
import org.syu_likelion.Festa_2026.error.ApiException;
import org.syu_likelion.Festa_2026.user.FestivalRole;
import org.syu_likelion.Festa_2026.user.UserDtos.MeResponse;
import org.syu_likelion.Festa_2026.user.UserService;

class FrontendAnalyticsServiceTests {
    private static final Instant NOW = Instant.parse("2026-08-10T12:00:00Z");
    private static final UUID USER_UUID = UUID.fromString("123e4567-e89b-12d3-a456-426614174099");
    private FrontendAnalyticsService service;
    private AsyncFrontendEventWriter writer;
    private UserService users;

    @BeforeEach
    void setUp() {
        writer = mock(AsyncFrontendEventWriter.class);
        users = mock(UserService.class);
        when(users.getMe("access", "refresh")).thenReturn(new AuthorizedResult<>(me(), "new-access", null));
        when(writer.publish(any())).thenReturn(true);
        service = new FrontendAnalyticsService(properties(), writer, users,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void authenticatedBatchIsQueuedWithServerDerivedUserUuid() {
        EventBatchRequest batch = new EventBatchRequest(UUID.randomUUID(), "2026.10.0", List.of(
                event(FrontendEventType.PAGE_VIEW, "/performances", null),
                event(FrontendEventType.PERFORMANCE_DETAIL_VIEW, "/performances/:id", "15")));

        var result = service.ingest("access", "refresh", batch);

        assertThat(result.body().acceptedEvents()).isEqualTo(2);
        assertThat(result.newAccessToken()).isEqualTo("new-access");
        ArgumentCaptor<FrontendEventRecord> captor = ArgumentCaptor.forClass(FrontendEventRecord.class);
        verify(writer, org.mockito.Mockito.times(2)).publish(captor.capture());
        assertThat(captor.getAllValues()).allSatisfy(record -> {
            assertThat(record.userUuid()).isEqualTo(USER_UUID);
            assertThat(record.sessionId()).isEqualTo(batch.sessionId());
            assertThat(record.appVersion()).isEqualTo("2026.10.0");
        });
    }

    @Test
    void anonymousBatchIsQueuedWithoutCallingSso() {
        EventBatchRequest batch = new EventBatchRequest(UUID.randomUUID(), null,
                List.of(event(FrontendEventType.PAGE_VIEW, "/", null)));

        var result = service.ingest(null, null, batch);

        assertThat(result.body().acceptedEvents()).isEqualTo(1);
        verify(users, never()).getMe(any(), any());
        ArgumentCaptor<FrontendEventRecord> captor = ArgumentCaptor.forClass(FrontendEventRecord.class);
        verify(writer).publish(captor.capture());
        assertThat(captor.getValue().userUuid()).isNull();
    }

    @Test
    void queryStringAndUnexpectedTargetAreRejected() {
        EventBatchRequest query = new EventBatchRequest(UUID.randomUUID(), null,
                List.of(event(FrontendEventType.PAGE_VIEW, "/search?keyword=private", null)));
        EventBatchRequest target = new EventBatchRequest(UUID.randomUUID(), null,
                List.of(event(FrontendEventType.PAGE_VIEW, "/performances", "15")));

        assertInvalid(query);
        assertInvalid(target);
        verify(writer, never()).publish(any());
    }

    @Test
    void duplicateEventIdAndOutOfRangeTimeAreRejected() {
        UUID duplicated = UUID.randomUUID();
        FrontendEventRequest first = new FrontendEventRequest(duplicated, FrontendEventType.PAGE_VIEW,
                "/", null, NOW, null);
        FrontendEventRequest second = new FrontendEventRequest(duplicated, FrontendEventType.MAP_VIEW,
                "/map", null, NOW, null);
        assertInvalid(new EventBatchRequest(UUID.randomUUID(), null, List.of(first, second)));

        FrontendEventRequest old = new FrontendEventRequest(UUID.randomUUID(), FrontendEventType.PAGE_VIEW,
                "/", null, NOW.minus(Duration.ofHours(25)), null);
        assertInvalid(new EventBatchRequest(UUID.randomUUID(), null, List.of(old)));
    }

    @Test
    void anonymousSessionRateLimitIsAppliedBeforeSsoAndQueue() {
        UserService limitedUsers = mock(UserService.class);
        AsyncFrontendEventWriter limitedWriter = mock(AsyncFrontendEventWriter.class);
        FrontendAnalyticsService limited = new FrontendAnalyticsService(properties(1),
                limitedWriter, limitedUsers, Clock.fixed(NOW, ZoneOffset.UTC));
        EventBatchRequest batch = new EventBatchRequest(UUID.randomUUID(), null, List.of(
                event(FrontendEventType.PAGE_VIEW, "/", null),
                event(FrontendEventType.MAP_VIEW, "/map", null)));

        assertThatThrownBy(() -> limited.ingest(null, null, batch))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.status().value()).isEqualTo(429);
                    assertThat(exception.code()).isEqualTo("FRONTEND_ANALYTICS_RATE_LIMIT");
                });
        verify(limitedUsers, never()).getMe(any(), any());
        verify(limitedWriter, never()).publish(any());
    }

    private void assertInvalid(EventBatchRequest request) {
        assertThatThrownBy(() -> service.ingest("access", "refresh", request))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.status().value()).isEqualTo(400);
                    assertThat(exception.code()).isEqualTo("INVALID_FRONTEND_EVENT");
                });
    }

    private FrontendEventRequest event(FrontendEventType type, String route, String targetId) {
        return new FrontendEventRequest(UUID.randomUUID(), type, route, targetId, NOW, null);
    }

    private FrontendAnalyticsProperties properties() {
        return properties(120);
    }

    private FrontendAnalyticsProperties properties(int maxEventsPerMinute) {
        return new FrontendAnalyticsProperties(true, 100, 20, Duration.ofMillis(50),
                Duration.ofDays(60), Duration.ofHours(6), 100, 1,
                Duration.ofHours(24), Duration.ofMinutes(5), Duration.ofHours(12),
                maxEventsPerMinute, 100_000, Duration.ofMinutes(10));
    }

    private MeResponse me() {
        return new MeResponse(USER_UUID, "festival01", "user@example.com", "USER", "ACTIVE",
                null, null, null, null, null, null, null, null, null, Set.of(FestivalRole.USER));
    }
}
