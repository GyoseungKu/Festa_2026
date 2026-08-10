package org.syu_likelion.Festa_2026.monitoring;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.syu_likelion.Festa_2026.error.ApiException;

@Service
public class PresenceTracker {
    private final ConcurrentHashMap<UUID, Presence> sessions = new ConcurrentHashMap<>();
    private final MonitoringProperties properties;
    private final Clock clock;

    public PresenceTracker(MonitoringProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public void heartbeat(UUID sessionId, String route) {
        Instant now = Instant.now(clock);
        if (!sessions.containsKey(sessionId) && sessions.size() >= properties.presenceMaxSessions()) {
            cleanup(now);
            if (sessions.size() >= properties.presenceMaxSessions()) {
                throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "PRESENCE_CAPACITY_EXCEEDED",
                        "접속 현황 수집이 일시적으로 혼잡합니다.");
            }
        }
        sessions.put(sessionId, new Presence(route, now));
    }

    public PresenceSnapshot snapshot() {
        Instant now = Instant.now(clock);
        cleanup(now);
        List<RoutePresence> routes = sessions.values().stream()
                .collect(java.util.stream.Collectors.groupingBy(Presence::route,
                        java.util.stream.Collectors.counting()))
                .entrySet().stream()
                .map(entry -> new RoutePresence(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparingLong(RoutePresence::activeSessions).reversed()
                        .thenComparing(RoutePresence::route))
                .limit(20)
                .toList();
        return new PresenceSnapshot(sessions.size(), properties.presenceTtl().toSeconds(), routes);
    }

    @Scheduled(fixedDelayString = "${monitoring.presence-cleanup-interval-ms:30000}")
    void scheduledCleanup() { cleanup(Instant.now(clock)); }

    private void cleanup(Instant now) {
        Instant cutoff = now.minus(properties.presenceTtl());
        sessions.entrySet().removeIf(entry -> entry.getValue().lastSeen().isBefore(cutoff));
    }

    private record Presence(String route, Instant lastSeen) { }

    public record RoutePresence(String route, long activeSessions) { }

    public record PresenceSnapshot(int activeSessions, long ttlSeconds, List<RoutePresence> routes) { }
}
