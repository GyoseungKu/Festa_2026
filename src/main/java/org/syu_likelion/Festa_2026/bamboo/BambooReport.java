package org.syu_likelion.Festa_2026.bamboo;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import org.syu_likelion.Festa_2026.performance.KstInstantAttributeConverter;

/**
 * 대나무숲 신고.
 *
 * <p>메시지와 JPA 연관을 맺지 않고 {@code messageId}만 보관한다.
 * 관리자 목록은 집계 조회이므로 연관을 두면 조회 경로에 지연 로딩이 들어간다.
 */
@Entity
@Table(name = "bamboo_reports",
        uniqueConstraints = @UniqueConstraint(name = "uk_bamboo_report",
                columnNames = {"message_id", "user_uuid"}))
public class BambooReport {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "message_id", nullable = false, updatable = false)
    private Long messageId;

    @Column(name = "user_uuid", nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID userUuid;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BambooReportReason reason;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Convert(converter = KstInstantAttributeConverter.class)
    private Instant createdAt;

    protected BambooReport() { }

    BambooReport(Long messageId, UUID userUuid, BambooReportReason reason, Instant createdAt) {
        this.messageId = messageId;
        this.userUuid = userUuid;
        this.reason = reason;
        this.createdAt = createdAt;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getMessageId() { return messageId; }
    public UUID getUserUuid() { return userUuid; }
    public BambooReportReason getReason() { return reason; }
    public Instant getCreatedAt() { return createdAt; }
}
