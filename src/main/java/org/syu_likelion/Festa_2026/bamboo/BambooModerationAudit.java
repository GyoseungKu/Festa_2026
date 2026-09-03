package org.syu_likelion.Festa_2026.bamboo;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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
import org.syu_likelion.Festa_2026.user.FestivalRole;
import org.syu_likelion.Festa_2026.performance.KstInstantAttributeConverter;

/** 대나무숲 차단·해제 조치를 사후 확인하기 위한 불변 감사 기록. */
@Entity
@Table(name = "bamboo_moderation_audits", indexes = {
        @Index(name = "idx_bamboo_audit_occurred", columnList = "occurred_at"),
        @Index(name = "idx_bamboo_audit_target", columnList = "target_user_uuid, occurred_at")
})
public class BambooModerationAudit {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "target_user_uuid", nullable = false, columnDefinition = "BINARY(16)")
    private UUID targetUserUuid;
    @Column(name = "target_nickname", nullable = false, length = 20)
    private String targetNickname;
    @Column(name = "actor_uuid", nullable = false, columnDefinition = "BINARY(16)")
    private UUID actorUuid;
    @Column(name = "actor_name", nullable = false, length = 100)
    private String actorName;
    @Enumerated(EnumType.STRING) @Column(name = "actor_role", nullable = false, length = 20)
    private FestivalRole actorRole;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 12)
    private BambooModerationAction action;
    @Column(name = "duration_minutes")
    private Integer durationMinutes;
    @Column(nullable = false, length = 200)
    private String reason;
    @Column(name = "source_message_id")
    private Long sourceMessageId;
    @Column(name = "occurred_at", nullable = false)
    @Convert(converter = KstInstantAttributeConverter.class)
    private Instant occurredAt;

    protected BambooModerationAudit() { }

    BambooModerationAudit(UUID targetUserUuid, String targetNickname, UUID actorUuid, String actorName,
                          FestivalRole actorRole, BambooModerationAction action, Integer durationMinutes,
                          String reason, Long sourceMessageId, Instant occurredAt) {
        this.targetUserUuid = targetUserUuid;
        this.targetNickname = targetNickname;
        this.actorUuid = actorUuid;
        this.actorName = actorName;
        this.actorRole = actorRole;
        this.action = action;
        this.durationMinutes = durationMinutes;
        this.reason = reason;
        this.sourceMessageId = sourceMessageId;
        this.occurredAt = occurredAt;
    }

    public Long getId() { return id; }
    public UUID getTargetUserUuid() { return targetUserUuid; }
    public String getTargetNickname() { return targetNickname; }
    public UUID getActorUuid() { return actorUuid; }
    public String getActorName() { return actorName; }
    public FestivalRole getActorRole() { return actorRole; }
    public BambooModerationAction getAction() { return action; }
    public Integer getDurationMinutes() { return durationMinutes; }
    public String getReason() { return reason; }
    public Long getSourceMessageId() { return sourceMessageId; }
    public Instant getOccurredAt() { return occurredAt; }
}
