package org.syu_likelion.Festa_2026.logging;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "api_request_logs", indexes = {
        @Index(name = "idx_api_log_occurred_at", columnList = "occurred_at"),
        @Index(name = "idx_api_log_user_time", columnList = "user_uuid,occurred_at"),
        @Index(name = "idx_api_log_request_id", columnList = "request_id", unique = true)
})
class ApiRequestLogEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_id", nullable = false, length = 36, updatable = false)
    private String requestId;

    @Column(name = "user_uuid", columnDefinition = "BINARY(16)", updatable = false)
    private UUID userUuid;

    @Column(name = "client_ip", nullable = false, length = 45, updatable = false)
    private String clientIp;

    @Column(name = "http_method", nullable = false, length = 10, updatable = false)
    private String httpMethod;

    @Column(name = "request_path", nullable = false, length = 1024, updatable = false)
    private String requestPath;

    @Column(name = "route_pattern", length = 512, updatable = false)
    private String routePattern;

    @Column(name = "response_status", nullable = false, updatable = false)
    private int responseStatus;

    @Column(name = "duration_ms", nullable = false, updatable = false)
    private long durationMs;

    @Column(nullable = false, length = 255, updatable = false)
    private String host;

    @Column(nullable = false, length = 10, updatable = false)
    private String scheme;

    @Column(name = "user_agent", length = 512, updatable = false)
    private String userAgent;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    protected ApiRequestLogEntity() { }
}
