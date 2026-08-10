package org.syu_likelion.Festa_2026.analytics;

import java.time.Instant;
import java.util.UUID;

record FrontendEventRecord(
        UUID eventId,
        String requestId,
        UUID userUuid,
        UUID sessionId,
        FrontendEventType eventType,
        String route,
        String targetId,
        Long durationMs,
        Instant clientOccurredAt,
        Instant receivedAt,
        String appVersion) { }
