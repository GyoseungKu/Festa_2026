package org.syu_likelion.Festa_2026.logging;

import java.time.Instant;
import java.util.UUID;

public record ApiRequestLogRecord(
        String requestId,
        UUID userUuid,
        String clientIp,
        String httpMethod,
        String requestPath,
        String routePattern,
        int responseStatus,
        long durationMs,
        String host,
        String scheme,
        String userAgent,
        Instant occurredAt) { }
