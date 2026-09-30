package org.syu_likelion.Festa_2026.wristband;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "festival_wristband_events", indexes = @Index(name = "idx_wristband_events_record", columnList = "wristband_id,id"))
public class WristbandEvent {
    public enum Action { ISSUE, REVOKE }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private Long wristbandId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private Action action;
    @JdbcTypeCode(SqlTypes.CHAR) @Column(nullable = false, length = 36, columnDefinition = "CHAR(36)")
    private UUID targetUserUuid;
    @JdbcTypeCode(SqlTypes.CHAR) @Column(nullable = false, length = 36, columnDefinition = "CHAR(36)")
    private UUID actorUuid;
    @Column(nullable = false, length = 200) private String actorName;
    @Column(columnDefinition = "TEXT") private String reason;
    @Column(nullable = false) private Instant occurredAt;
    protected WristbandEvent() { }
    public WristbandEvent(Wristband record, Action action, UUID actor, String actorName, String reason, Instant now) {
        wristbandId = record.getId(); this.action = action; targetUserUuid = record.getTargetUserUuid();
        actorUuid = actor; this.actorName = actorName; this.reason = reason; occurredAt = now;
    }
    public Long getId() { return id; }
    public Action getAction() { return action; }
    public UUID getTargetUserUuid() { return targetUserUuid; }
    public UUID getActorUuid() { return actorUuid; }
    public String getActorName() { return actorName; }
    public String getReason() { return reason; }
    public Instant getOccurredAt() { return occurredAt; }
}
