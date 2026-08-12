package org.syu_likelion.Festa_2026.monitoring;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PresenceTrackerTests {
    @Test
    void heartbeatCountsAnonymousSessionsByRouteAndExpiresThem() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-10T00:00:00Z"));
        PresenceTracker tracker = new PresenceTracker(
                new MonitoringProperties(Duration.ofSeconds(150), 10, 2), clock);
        UUID first = UUID.randomUUID();
        tracker.heartbeat(first, "/performances");
        tracker.heartbeat(UUID.randomUUID(), "/performances");
        tracker.heartbeat(UUID.randomUUID(), "/map");

        var current = tracker.snapshot();
        assertThat(current.activeSessions()).isEqualTo(3);
        assertThat(current.routes()).extracting(PresenceTracker.RoutePresence::route)
                .containsExactly("/performances", "/map");

        clock.advance(Duration.ofSeconds(151));
        assertThat(tracker.snapshot().activeSessions()).isZero();
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
