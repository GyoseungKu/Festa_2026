package org.syu_likelion.Festa_2026.stamp;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "festival_stamp_prizes", uniqueConstraints =
        @UniqueConstraint(name = "uk_stamp_prize_target", columnNames = "target_user_uuid"))
public class StampPrize {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "target_user_uuid", nullable = false, columnDefinition = "BINARY(16)")
    private UUID targetUserUuid;
    @Column(name = "granted_by", nullable = false, columnDefinition = "BINARY(16)")
    private UUID grantedBy;
    @Column(name = "granted_at", nullable = false)
    private Instant grantedAt;
    @Column(name = "stamp_count", nullable = false)
    private int stampCount;

    protected StampPrize() { }
    StampPrize(UUID targetUserUuid, UUID grantedBy, Instant grantedAt, int stampCount) {
        this.targetUserUuid = targetUserUuid;
        this.grantedBy = grantedBy;
        this.grantedAt = grantedAt;
        this.stampCount = stampCount;
    }
    public Long getId() { return id; }
    public UUID getTargetUserUuid() { return targetUserUuid; }
    public UUID getGrantedBy() { return grantedBy; }
    public Instant getGrantedAt() { return grantedAt; }
    public int getStampCount() { return stampCount; }
}
