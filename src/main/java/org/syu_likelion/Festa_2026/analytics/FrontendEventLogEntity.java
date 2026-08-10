package org.syu_likelion.Festa_2026.analytics;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "frontend_event_logs", indexes = {
        @Index(name = "idx_front_event_id", columnList = "event_id", unique = true),
        @Index(name = "idx_front_received_at", columnList = "received_at"),
        @Index(name = "idx_front_type_time", columnList = "event_type,received_at"),
        @Index(name = "idx_front_route_time", columnList = "route,received_at")
})
class FrontendEventLogEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, columnDefinition = "BINARY(16)", updatable = false)
    private UUID eventId;

    @Column(name = "request_id", nullable = false, length = 36, updatable = false)
    private String requestId;

    @Column(name = "user_uuid", nullable = false, columnDefinition = "BINARY(16)", updatable = false)
    private UUID userUuid;

    @Column(name = "session_id", nullable = false, columnDefinition = "BINARY(16)", updatable = false)
    private UUID sessionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 40, updatable = false)
    private FrontendEventType eventType;

    @Column(nullable = false, length = 200, updatable = false)
    private String route;

    @Column(name = "target_id", length = 100, updatable = false)
    private String targetId;

    @Column(name = "duration_ms", updatable = false)
    private Long durationMs;

    @Column(name = "client_occurred_at", nullable = false, updatable = false)
    private Instant clientOccurredAt;

    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    @Column(name = "app_version", length = 50, updatable = false)
    private String appVersion;

    protected FrontendEventLogEntity() { }
}
