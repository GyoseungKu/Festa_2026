package org.syu_likelion.Festa_2026.analytics;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("frontend-analytics")
public record FrontendAnalyticsProperties(
        boolean enabled,
        int queueCapacity,
        int batchSize,
        Duration flushInterval,
        Duration retention,
        Duration cleanupInterval,
        int cleanupBatchSize,
        int cleanupMaxBatches,
        Duration maximumPastAge,
        Duration maximumFutureSkew,
        Duration maximumDuration,
        int maxEventsPerMinute,
        int maxTrackedSessions,
        Duration sessionTrackingTtl) {

    public FrontendAnalyticsProperties {
        queueCapacity = positive(queueCapacity, 20_000);
        batchSize = positive(batchSize, 500);
        flushInterval = positive(flushInterval, Duration.ofMillis(500));
        retention = positive(retention, Duration.ofDays(60));
        cleanupInterval = positive(cleanupInterval, Duration.ofMinutes(5));
        cleanupBatchSize = positive(cleanupBatchSize, 5_000);
        cleanupMaxBatches = positive(cleanupMaxBatches, 4);
        maximumPastAge = positive(maximumPastAge, Duration.ofHours(24));
        maximumFutureSkew = positive(maximumFutureSkew, Duration.ofMinutes(5));
        maximumDuration = positive(maximumDuration, Duration.ofHours(12));
        maxEventsPerMinute = positive(maxEventsPerMinute, 120);
        maxTrackedSessions = positive(maxTrackedSessions, 100_000);
        sessionTrackingTtl = positive(sessionTrackingTtl, Duration.ofMinutes(10));
    }

    private static int positive(int value, int fallback) {
        return value > 0 ? value : fallback;
    }

    private static Duration positive(Duration value, Duration fallback) {
        return value != null && !value.isNegative() && !value.isZero() ? value : fallback;
    }
}
