package org.syu_likelion.Festa_2026.stamp;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "festival_stamp_prize_events", indexes = @Index(name = "idx_stamp_prize_event", columnList = "prize_id,occurred_at,id"))
public class StampPrizeEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "prize_id", nullable = false) private StampPrize prize;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private StampAction action;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private StampMethod method;
    @Column(name = "actor_uuid", nullable = false, columnDefinition = "BINARY(16)") private UUID actorUuid;
    @Column(name = "occurred_at", nullable = false) private Instant occurredAt;
    @Column(name = "stamp_count", nullable = false) private int stampCount;
    @Column(length = 500) private String reason;
    protected StampPrizeEvent() { }
    StampPrizeEvent(StampPrize prize, StampAction action, StampMethod method, UUID actor, Instant at, String reason) {
        this.prize = prize; this.action = action; this.method = method; this.actorUuid = actor;
        this.occurredAt = at; this.stampCount = prize.getStampCount(); this.reason = reason;
    }
    public Long getId() { return id; }
    public StampAction getAction() { return action; }
    public StampMethod getMethod() { return method; }
    public UUID getActorUuid() { return actorUuid; }
    public Instant getOccurredAt() { return occurredAt; }
    public int getStampCount() { return stampCount; }
    public String getReason() { return reason; }
}
