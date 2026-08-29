package org.syu_likelion.Festa_2026.bamboo;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

/** 시간 기반 제한을 검증하기 위한 수동 진행 시계. */
final class MutableClock extends Clock {
    private Instant now;
    private final ZoneId zone;

    MutableClock(Instant start) { this(start, ZoneId.of("UTC")); }

    private MutableClock(Instant start, ZoneId zone) {
        this.now = start;
        this.zone = zone;
    }

    void advance(Duration amount) { now = now.plus(amount); }

    @Override public ZoneId getZone() { return zone; }
    @Override public Clock withZone(ZoneId other) { return new MutableClock(now, other); }
    @Override public Instant instant() { return now; }
}
