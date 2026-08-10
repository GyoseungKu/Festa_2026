package org.syu_likelion.Festa_2026.logging;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("api-request-log")
public record ApiRequestLogProperties(
        boolean enabled,
        int queueCapacity,
        int batchSize,
        Duration flushInterval,
        Duration retention,
        Duration cleanupInterval,
        int cleanupBatchSize,
        int cleanupMaxBatches,
        double successfulReadSampleRate,
        Duration slowRequestThreshold,
        boolean trustForwardedHeaders) {

    public ApiRequestLogProperties {
        queueCapacity = positive(queueCapacity, 10_000);
        batchSize = positive(batchSize, 250);
        flushInterval = positive(flushInterval, Duration.ofMillis(250));
        retention = positive(retention, Duration.ofDays(60));
        cleanupInterval = positive(cleanupInterval, Duration.ofMinutes(5));
        cleanupBatchSize = positive(cleanupBatchSize, 5_000);
        cleanupMaxBatches = positive(cleanupMaxBatches, 4);
        successfulReadSampleRate = Math.max(0, Math.min(1, successfulReadSampleRate));
        slowRequestThreshold = positive(slowRequestThreshold, Duration.ofSeconds(1));
    }

    private static int positive(int value, int fallback) {
        return value > 0 ? value : fallback;
    }

    private static Duration positive(Duration value, Duration fallback) {
        return value != null && !value.isNegative() && !value.isZero() ? value : fallback;
    }
}
