package org.syu_likelion.Festa_2026.monitoring;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class HttpLiveMetricsTests {
    @Test
    void aggregatesOnlyTheLatestMinuteWithoutDatabaseWrites() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-10T00:00:00Z"));
        HttpLiveMetrics metrics = new HttpLiveMetrics(clock);
        metrics.requestStarted();
        metrics.requestFinished(200, 80);
        metrics.requestStarted();
        metrics.requestFinished(503, 800);

        var snapshot = metrics.snapshot();
        assertThat(snapshot.requestsLastMinute()).isEqualTo(2);
        assertThat(snapshot.serverErrors()).isEqualTo(1);
        assertThat(snapshot.errorRatePercent()).isEqualTo(50);
        assertThat(snapshot.p95LatencyMs()).isEqualTo(1_000);
        assertThat(snapshot.inFlight()).isZero();

        clock.advance(Duration.ofSeconds(61));
        assertThat(metrics.snapshot().requestsLastMinute()).isZero();
    }

    private static final class MutableClock extends Clock {
        private Instant instant;
        MutableClock(Instant instant) { this.instant = instant; }
        void advance(Duration duration) { instant = instant.plus(duration); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }
    }
}
