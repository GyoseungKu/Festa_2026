package org.syu_likelion.Festa_2026.notice;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.syu_likelion.Festa_2026.performance.KstInstantAttributeConverter;

@Entity
@Table(name = "notices", indexes = {
        @Index(name = "idx_notice_pin_created", columnList = "pinned,pinned_at,created_at")
})
public class Notice {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;


    @Column(nullable = false)
    private boolean pinned;

    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean banner;

    @Column(name = "pinned_at")
    @Convert(converter = KstInstantAttributeConverter.class)
    private Instant pinnedAt;

    @Column(name = "view_count", nullable = false)
    private long viewCount;

    @Column(name = "author_uuid", nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID authorUuid;

    @Column(name = "author_name", nullable = false, updatable = false, length = 100)
    private String authorName;

    @Column(name = "last_modified_by_uuid", nullable = false, columnDefinition = "BINARY(16)")
    private UUID lastModifiedByUuid;

    @OneToMany(mappedBy = "notice", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderColumn(name = "display_order")
    private List<NoticeAttachment> attachments = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    @Convert(converter = KstInstantAttributeConverter.class)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    @Convert(converter = KstInstantAttributeConverter.class)
    private Instant updatedAt;

    protected Notice() { }

    Notice(String title, String content, boolean pinned,
                   UUID actorUuid, String authorName, Instant now) {
        this.authorUuid = actorUuid;
        this.authorName = authorName;
        update(title, content, pinned, actorUuid, now);
    }

    void update(String title, String content, boolean pinned,
                UUID actorUuid, Instant now) {
        this.title = title;
        this.content = content;
        if (pinned && !this.pinned) this.pinnedAt = now;
        if (!pinned) this.pinnedAt = null;
        this.pinned = pinned;
        this.lastModifiedByUuid = actorUuid;
    }


    void changePinned(boolean pinned, UUID actorUuid, Instant now) {
        if (pinned && !this.pinned) this.pinnedAt = now;
        if (!pinned) this.pinnedAt = null;
        this.pinned = pinned;
        this.lastModifiedByUuid = actorUuid;
    }

    void addAttachment(NoticeAttachment attachment) {
        attachment.attachTo(this);
        attachments.add(attachment);
    }

    void removeAttachment(NoticeAttachment attachment) {
        attachments.remove(attachment);
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getContent() { return content; }
    public boolean isPinned() { return pinned; }
    public boolean isBanner() { return banner; }
    void changeBanner(boolean banner) { this.banner = banner; }
    public Instant getPinnedAt() { return pinnedAt; }
    public long getViewCount() { return viewCount; }
    public UUID getAuthorUuid() { return authorUuid; }
    public String getAuthorName() { return authorName; }
    public UUID getLastModifiedByUuid() { return lastModifiedByUuid; }
    public List<NoticeAttachment> getAttachments() { return List.copyOf(attachments); }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
