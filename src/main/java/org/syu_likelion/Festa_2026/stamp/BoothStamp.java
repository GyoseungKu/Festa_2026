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
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import org.syu_likelion.Festa_2026.booth.FestivalBooth;
import org.syu_likelion.Festa_2026.user.FestivalUser;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Entity
@Table(name = "festival_booth_stamps", uniqueConstraints =
        @UniqueConstraint(name = "uk_booth_stamp_user", columnNames = {"booth_id", "festival_user_id"}))
public class BoothStamp {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booth_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private FestivalBooth booth;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "festival_user_id", nullable = false)
    private FestivalUser user;
    @Column(name = "granted_at", nullable = false)
    private Instant grantedAt;
    @Column(name = "granted_by", nullable = false, columnDefinition = "BINARY(16)")
    private UUID grantedBy;
    @Enumerated(EnumType.STRING)
    @Column(name = "grant_method", nullable = false, length = 20)
    private StampMethod grantMethod;

    protected BoothStamp() { }
    BoothStamp(FestivalBooth booth, FestivalUser user, Instant grantedAt, UUID grantedBy, StampMethod method) {
        this.booth = booth; this.user = user; this.grantedAt = grantedAt; this.grantedBy = grantedBy; this.grantMethod = method;
    }
    public Long getId() { return id; }
    public FestivalBooth getBooth() { return booth; }
    public FestivalUser getUser() { return user; }
    public Instant getGrantedAt() { return grantedAt; }
    public UUID getGrantedBy() { return grantedBy; }
    public StampMethod getGrantMethod() { return grantMethod; }
}
