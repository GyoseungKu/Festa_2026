package org.syu_likelion.Festa_2026.logging;

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

class AsyncApiRequestLogWriterTests {

    @Test
    @SuppressWarnings("unchecked")
    void drainsQueuedRecordsIntoOneJdbcBatch() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ApiRequestLogProperties properties = new ApiRequestLogProperties(true, 100, 3,
                Duration.ofMillis(50), Duration.ofDays(60), Duration.ofHours(6), 100, 1,
                1.0, Duration.ofSeconds(1), false);
        AsyncApiRequestLogWriter writer = new AsyncApiRequestLogWriter(properties, jdbc,
                Clock.fixed(Instant.parse("2026-08-10T12:00:00Z"), ZoneOffset.UTC));
        writer.publish(record("one"));
        writer.publish(record("two"));
        writer.publish(record("three"));

        try {
            writer.start();
            verify(jdbc, timeout(2_000)).batchUpdate(anyString(),
                    org.mockito.ArgumentMatchers.argThat(records -> records.size() == 3), eq(3),
                    any(ParameterizedPreparedStatementSetter.class));
        } finally {
            writer.stop();
        }
    }

    private ApiRequestLogRecord record(String suffix) {
        return new ApiRequestLogRecord(UUID.randomUUID().toString(), null, "127.0.0.1", "GET",
                "/api/" + suffix, "/api/{name}", 200, 5, "localhost", "http",
                "test-agent", Instant.parse("2026-08-10T12:00:00Z"));
    }
}
