package org.syu_likelion.Festa_2026.birthday;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.syu_likelion.Festa_2026.performance.KstInstantAttributeConverter;

@Entity
@Table(name = "birthday_messages",
        uniqueConstraints = @UniqueConstraint(name = "uk_birthday_message_author", columnNames = "author_uuid"),
        indexes = {
                @Index(name = "idx_birthday_message_created", columnList = "created_at"),
                @Index(name = "idx_birthday_message_hearts_created", columnList = "heart_count,created_at")
        })
public class BirthdayMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "author_uuid", nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID authorUuid;

    @Column(nullable = false, length = 400)
    private String content;

    @Column(name = "public_department", length = 150)
    private String publicDepartment;

    @Column(name = "public_masked_student_no", nullable = false, length = 100)
    private String publicMaskedStudentNo;

    @Column(name = "public_masked_name", nullable = false, length = 100)
    private String publicMaskedName;

    @Column(name = "heart_count", nullable = false)
    private long heartCount;

    @OneToMany(mappedBy = "message", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<BirthdayMessageHeart> hearts = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    @Convert(converter = KstInstantAttributeConverter.class)
    private Instant createdAt;

    protected BirthdayMessage() { }

    BirthdayMessage(UUID authorUuid, String content, String publicDepartment,
                    String publicMaskedStudentNo, String publicMaskedName) {
        this.authorUuid = authorUuid;
        this.content = content;
        this.publicDepartment = publicDepartment;
        this.publicMaskedStudentNo = publicMaskedStudentNo;
        this.publicMaskedName = publicMaskedName;
    }

    @PrePersist
    void prePersist() { createdAt = Instant.now(); }

    public Long getId() { return id; }
    public UUID getAuthorUuid() { return authorUuid; }
    public String getContent() { return content; }
    public String getPublicDepartment() { return publicDepartment; }
    public String getPublicMaskedStudentNo() { return publicMaskedStudentNo; }
    public String getPublicMaskedName() { return publicMaskedName; }
    public long getHeartCount() { return heartCount; }
    public Instant getCreatedAt() { return createdAt; }
}
