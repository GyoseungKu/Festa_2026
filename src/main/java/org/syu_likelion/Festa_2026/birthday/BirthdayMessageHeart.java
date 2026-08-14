package org.syu_likelion.Festa_2026.birthday;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import org.syu_likelion.Festa_2026.performance.KstInstantAttributeConverter;

@Entity
@Table(name = "birthday_message_hearts",
        uniqueConstraints = @UniqueConstraint(name = "uk_birthday_heart_user",
                columnNames = {"message_id", "user_uuid"}),
        indexes = @Index(name = "idx_birthday_heart_message_created", columnList = "message_id,created_at"))
public class BirthdayMessageHeart {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "message_id", nullable = false)
    private BirthdayMessage message;

    @Column(name = "user_uuid", nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID userUuid;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Convert(converter = KstInstantAttributeConverter.class)
    private Instant createdAt;

    protected BirthdayMessageHeart() { }
    BirthdayMessageHeart(BirthdayMessage message, UUID userUuid) {
        this.message = message;
        this.userUuid = userUuid;
    }

    @PrePersist
    void prePersist() { createdAt = Instant.now(); }

    public Long getId() { return id; }
    public Long getMessageId() { return message.getId(); }
    public UUID getUserUuid() { return userUuid; }
    public Instant getCreatedAt() { return createdAt; }
}
