package org.syu_likelion.Festa_2026.analytics;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.syu_likelion.Festa_2026.analytics.*;

import org.syu_likelion.Festa_2026.error.ApiException;

import org.syu_likelion.Festa_2026.user.UserService;

/** Documents observed weaknesses for the 2026-09-16 audit; invert assertions when fixing them. */
class SecurityAuditCharacterizationTests {
    private static final Instant NOW = Instant.parse("2026-09-16T00:00:00Z");

    @Test void changingClientChosenSessionIdBypassesAnonymousAnalyticsRateLimit() {
        var properties = new FrontendAnalyticsProperties(true, 100, 20, Duration.ofMillis(50),
                Duration.ofDays(60), Duration.ofHours(6), 100, 1, Duration.ofHours(24),
                Duration.ofMinutes(5), Duration.ofHours(12), 1, 100_000, Duration.ofMinutes(10));
        var writer = mock(AsyncFrontendEventWriter.class);
        when(writer.publish(any())).thenReturn(true);
        var analytics = new FrontendAnalyticsService(properties, writer, mock(UserService.class),
                Clock.fixed(NOW, ZoneOffset.UTC));
        UUID session = UUID.randomUUID();
        assertThat(analytics.ingest(null, null, batch(session)).body().acceptedEvents()).isEqualTo(1);
        assertThatThrownBy(() -> analytics.ingest(null, null, batch(session)))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.status().value()).isEqualTo(429));
        assertThat(analytics.ingest(null, null, batch(UUID.randomUUID())).body().acceptedEvents()).isEqualTo(1);
        verify(writer, times(2)).publish(any());
    }

    private FrontendAnalyticsDtos.EventBatchRequest batch(UUID session) {
        return new FrontendAnalyticsDtos.EventBatchRequest(session, null, List.of(
                new FrontendAnalyticsDtos.FrontendEventRequest(UUID.randomUUID(), FrontendEventType.PAGE_VIEW,
                        "/", null, NOW, null)));
    }
}
