package org.syu_likelion.Feata_2026.qr;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "festival_qr_tokens")
public class FestivalQrToken {
    @Id
    @Column(name = "token_hash", nullable = false, length = 64, updatable = false)
    private String tokenHash;

    @Column(name = "user_uuid", nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID userUuid;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected FestivalQrToken() { }

    FestivalQrToken(String tokenHash, UUID userUuid, Instant expiresAt, Instant createdAt) {
        this.tokenHash = tokenHash;
        this.userUuid = userUuid;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
    }

    public String getTokenHash() { return tokenHash; }
    public UUID getUserUuid() { return userUuid; }
    public Instant getExpiresAt() { return expiresAt; }

    boolean isExpiredAt(Instant now) {
        return !expiresAt.isAfter(now);
    }
}
