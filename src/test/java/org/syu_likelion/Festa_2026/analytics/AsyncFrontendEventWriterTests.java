package org.syu_likelion.Festa_2026.analytics;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ParameterizedPreparedStatementSetter;

class AsyncFrontendEventWriterTests {

    @Test
    @SuppressWarnings("unchecked")
    void drainsEventsIntoOneJdbcBatch() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        FrontendAnalyticsProperties properties = new FrontendAnalyticsProperties(true, 100, 3,
                Duration.ofMillis(50), Duration.ofDays(60), Duration.ofHours(6), 100, 1,
                Duration.ofHours(24), Duration.ofMinutes(5), Duration.ofHours(12),
                120, 100_000, Duration.ofMinutes(10));
        AsyncFrontendEventWriter writer = new AsyncFrontendEventWriter(properties, jdbc,
                Clock.fixed(Instant.parse("2026-08-10T12:00:00Z"), ZoneOffset.UTC));
        writer.publish(record("/one"));
        writer.publish(record("/two"));
        writer.publish(record("/three"));

        try {
            writer.start();
            verify(jdbc, timeout(2_000)).batchUpdate(anyString(),
                    org.mockito.ArgumentMatchers.argThat(events -> events.size() == 3), eq(3),
                    any(ParameterizedPreparedStatementSetter.class));
        } finally {
            writer.stop();
        }
    }

    private FrontendEventRecord record(String route) {
        Instant now = Instant.parse("2026-08-10T12:00:00Z");
        return new FrontendEventRecord(UUID.randomUUID(), UUID.randomUUID().toString(),
                UUID.randomUUID(), UUID.randomUUID(), FrontendEventType.PAGE_VIEW,
                route, null, null, now, now, "test");
    }
}
