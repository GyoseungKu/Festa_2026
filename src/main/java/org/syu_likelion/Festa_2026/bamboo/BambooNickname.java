package org.syu_likelion.Festa_2026.bamboo;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import org.syu_likelion.Festa_2026.performance.KstInstantAttributeConverter;

/**
 * 대나무숲 닉네임. 사용자당 하나이며 변경할 수 없다.
 *
 * <p>{@code nickname}은 화면 표시용 원본, {@code nicknameKey}는 중복 판정용 정규화 키다.
 * 닉네임이 없으면 작성 자체가 불가능하므로 작성 차단({@code mutedUntil})도 같은 행에서 관리한다.
 */
@Entity
@Table(name = "bamboo_nicknames",
        uniqueConstraints = @UniqueConstraint(name = "uk_bamboo_nickname_key", columnNames = "nickname_key"))
public class BambooNickname {
    @Id
    @Column(name = "user_uuid", nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID userUuid;

    @Column(nullable = false, length = 20)
    private String nickname;

    @Column(name = "nickname_key", nullable = false, length = 20)
    private String nicknameKey;

    @Column(name = "muted_until")
    @Convert(converter = KstInstantAttributeConverter.class)
    private Instant mutedUntil;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Convert(converter = KstInstantAttributeConverter.class)
    private Instant createdAt;

    protected BambooNickname() { }

    BambooNickname(UUID userUuid, String nickname, String nicknameKey, Instant createdAt) {
        this.userUuid = userUuid;
        this.nickname = nickname;
        this.nicknameKey = nicknameKey;
        this.createdAt = createdAt;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }

    /** 관리자 강제 변경 전용. 사용자는 닉네임을 바꿀 수 없다. */
    void rename(String nickname, String nicknameKey) {
        this.nickname = nickname;
        this.nicknameKey = nicknameKey;
    }

    void mute(Instant until) { this.mutedUntil = until; }

    public UUID getUserUuid() { return userUuid; }
    public String getNickname() { return nickname; }
    public String getNicknameKey() { return nicknameKey; }
    public Instant getMutedUntil() { return mutedUntil; }
    public Instant getCreatedAt() { return createdAt; }
}
