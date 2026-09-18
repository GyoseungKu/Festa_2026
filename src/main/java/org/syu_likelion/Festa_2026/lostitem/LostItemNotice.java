package org.syu_likelion.Festa_2026.lostitem;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "lost_item_notices", indexes = {
        @Index(name = "idx_lost_item_pin_created", columnList = "pinned,pinned_at,created_at"),
        @Index(name = "idx_lost_item_status_created", columnList = "status,created_at")
})
public class LostItemNotice {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "found_location", length = 200)
    private String foundLocation;

    public String getFoundLocation() { return foundLocation; }
    void setFoundLocation(String foundLocation) { this.foundLocation = foundLocation; }

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LostItemStatus status;

    @Column(nullable = false)
    private boolean pinned;

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
    private List<LostItemImage> images = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    @Convert(converter = KstInstantAttributeConverter.class)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    @Convert(converter = KstInstantAttributeConverter.class)
    private Instant updatedAt;

    protected LostItemNotice() { }

    LostItemNotice(String title, String content, LostItemStatus status, boolean pinned,
                   UUID actorUuid, String authorName, Instant now) {
        this.authorUuid = actorUuid;
        this.authorName = authorName;
        update(title, content, status, pinned, actorUuid, now);
    }

    void update(String title, String content, LostItemStatus status, boolean pinned,
                UUID actorUuid, Instant now) {
        this.title = title;
        this.content = content;
        this.status = status;
        if (pinned && !this.pinned) this.pinnedAt = now;
        if (!pinned) this.pinnedAt = null;
        this.pinned = pinned;
        this.lastModifiedByUuid = actorUuid;
    }

    void changeStatus(LostItemStatus status, UUID actorUuid) {
        this.status = status;
        this.lastModifiedByUuid = actorUuid;
    }

    void changePinned(boolean pinned, UUID actorUuid, Instant now) {
        if (pinned && !this.pinned) this.pinnedAt = now;
        if (!pinned) this.pinnedAt = null;
        this.pinned = pinned;
        this.lastModifiedByUuid = actorUuid;
    }

    void addImage(LostItemImage image) {
        image.attachTo(this);
        images.add(image);
    }

    void removeImage(LostItemImage image) {
        images.remove(image);
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
    public LostItemStatus getStatus() { return status; }
    public boolean isPinned() { return pinned; }
    public Instant getPinnedAt() { return pinnedAt; }
    public long getViewCount() { return viewCount; }
    public UUID getAuthorUuid() { return authorUuid; }
    public String getAuthorName() { return authorName; }
    public UUID getLastModifiedByUuid() { return lastModifiedByUuid; }
    public List<LostItemImage> getImages() { return List.copyOf(images); }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
