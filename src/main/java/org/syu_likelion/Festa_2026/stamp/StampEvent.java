package org.syu_likelion.Festa_2026.stamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Index;
import java.time.Instant;
import java.util.UUID;
import org.syu_likelion.Festa_2026.booth.FestivalBooth;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Entity
@Table(name = "festival_stamp_events", indexes = {
        @Index(name = "idx_stamp_event_booth_time", columnList = "booth_id, occurred_at"),
        @Index(name = "idx_stamp_event_target_action", columnList = "target_user_uuid, action")
})
public class StampEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booth_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private FestivalBooth booth;
    @Column(name = "target_user_uuid", nullable = false, columnDefinition = "BINARY(16)")
    private UUID targetUserUuid;
    @Column(name = "actor_uuid", nullable = false, columnDefinition = "BINARY(16)")
    private UUID actorUuid;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 12)
    private StampAction action;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private StampMethod method;
    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected StampEvent() { }
    StampEvent(FestivalBooth booth, UUID targetUserUuid, UUID actorUuid,
               StampAction action, StampMethod method, Instant occurredAt) {
        this.booth = booth; this.targetUserUuid = targetUserUuid; this.actorUuid = actorUuid;
        this.action = action; this.method = method; this.occurredAt = occurredAt;
    }
    public Long getId() { return id; }
    public FestivalBooth getBooth() { return booth; }
    public UUID getTargetUserUuid() { return targetUserUuid; }
    public UUID getActorUuid() { return actorUuid; }
    public StampAction getAction() { return action; }
    public StampMethod getMethod() { return method; }
    public Instant getOccurredAt() { return occurredAt; }
}
