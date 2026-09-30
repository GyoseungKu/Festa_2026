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
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import org.syu_likelion.Festa_2026.performance.KstInstantAttributeConverter;

/**
 * 대나무숲 메시지.
 *
 * <p>{@code id}는 불변 식별자로 신고·삭제 대상 지정과 과거 조회 커서에 사용하고,
 * {@code seq}는 변경 스트림 커서로 작성 시점과 상태 변경 시점에 각각 새로 발급한다.
 * AUTO_INCREMENT는 커밋 순서를 보장하지 않으므로 {@link BambooSequence}가 발급한다.
 */
@Entity
@Table(name = "bamboo_messages",
        uniqueConstraints = @UniqueConstraint(name = "uk_bamboo_message_seq", columnNames = "seq"),
        indexes = {
                @Index(name = "idx_bamboo_message_user", columnList = "user_uuid,created_at"),
                @Index(name = "idx_bamboo_message_reports", columnList = "report_count,id")
        })
public class BambooMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private long seq;

    @Column(name = "user_uuid", nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID userUuid;

    @Column(name = "anon_name", nullable = false, length = 20)
    private String anonName;

    // 본문 제한은 200 코드포인트다. 이모지는 UTF-16 기준 2단위이므로 컬럼은 그 두 배로 잡는다.
    // (birthday_messages 가 100 코드포인트 제한에 VARCHAR(400) 을 쓰는 것과 같은 이유)
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BambooMessageStatus status;

    @Column(name = "report_count", nullable = false)
    private int reportCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Convert(converter = KstInstantAttributeConverter.class)
    private Instant createdAt;

    @Column(name = "hidden_at")
    @Convert(converter = KstInstantAttributeConverter.class)
    private Instant hiddenAt;

    @Column(name = "deleted_at")
    @Convert(converter = KstInstantAttributeConverter.class)
    private Instant deletedAt;

    @Column(name = "deleted_by", columnDefinition = "BINARY(16)")
    private UUID deletedBy;

    protected BambooMessage() { }

    BambooMessage(long seq, UUID userUuid, String anonName, String content, Instant createdAt) {
        this.seq = seq;
        this.userUuid = userUuid;
        this.anonName = anonName;
        this.content = content;
        this.status = BambooMessageStatus.VISIBLE;
        this.createdAt = createdAt;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
        if (status == null) status = BambooMessageStatus.VISIBLE;
    }

    /** 상태를 바꾸면서 커서를 재발급한다. 변경이 신규 메시지와 같은 스트림으로 전달된다. */
    void changeStatus(BambooMessageStatus next, long newSeq, UUID actorUuid, Instant at) {
        this.status = next;
        this.seq = newSeq;
        if (next == BambooMessageStatus.HIDDEN || next == BambooMessageStatus.BLOCKED) this.hiddenAt = at;
        if (next == BambooMessageStatus.DELETED) {
            this.deletedAt = at;
            this.deletedBy = actorUuid;
        }
        if (next == BambooMessageStatus.VISIBLE) {
            this.hiddenAt = null;
            this.deletedAt = null;
            this.deletedBy = null;
        }
    }

    void renameAuthor(String anonName, long newSeq) {
        this.anonName = anonName;
        this.seq = newSeq;
    }

    public Long getId() { return id; }
    public long getSeq() { return seq; }
    public UUID getUserUuid() { return userUuid; }
    public String getAnonName() { return anonName; }
    public String getContent() { return content; }
    public BambooMessageStatus getStatus() { return status; }
    /** 기존 DB HIDDEN은 관리자 숨김이므로 원문을 공개하지 않는다. */
    public BambooMessageStatus getPublicStatus() {
        if (status == BambooMessageStatus.HIDDEN) return BambooMessageStatus.BLOCKED;
        if (status == BambooMessageStatus.VISIBLE && reportCount >= 5) return BambooMessageStatus.HIDDEN;
        return status;
    }

    void recordReport(long newSeq) {
        reportCount++;
        seq = newSeq;
    }

    public boolean canExposeContent() {
        return status == BambooMessageStatus.VISIBLE;
    }
    public int getReportCount() { return reportCount; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getHiddenAt() { return hiddenAt; }
    public Instant getDeletedAt() { return deletedAt; }
    public UUID getDeletedBy() { return deletedBy; }

    public boolean isVisible() { return status == BambooMessageStatus.VISIBLE; }
}
