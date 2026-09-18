package org.syu_likelion.Festa_2026.performance;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "festival_performances", indexes = {
        @Index(name = "idx_performance_published_at", columnList = "published_at"),
        @Index(name = "idx_performance_starts_at", columnList = "starts_at")
})
public class FestivalPerformance {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PerformanceCategory category;

    @Column(name = "team_name", nullable = false, length = 150)
    private String teamName;

    @ElementCollection
    @CollectionTable(name = "festival_performance_members",
            joinColumns = @JoinColumn(name = "performance_id"))
    @OrderColumn(name = "display_order")
    @Column(name = "member_name", nullable = false, length = 100)
    private List<String> memberNames = new ArrayList<>();

    @Column(name = "starts_at", nullable = false)
    @Convert(converter = KstInstantAttributeConverter.class)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    @Convert(converter = KstInstantAttributeConverter.class)
    private Instant endsAt;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @ElementCollection
    @CollectionTable(name = "festival_performance_links",
            joinColumns = @JoinColumn(name = "performance_id"))
    @OrderColumn(name = "display_order")
    @Column(name = "link_url", nullable = false, length = 2048)
    private List<String> links = new ArrayList<>();

    @OneToMany(mappedBy = "performance", cascade = jakarta.persistence.CascadeType.ALL,
            orphanRemoval = true)
    @OrderColumn(name = "display_order")
    private List<PerformanceMedia> media = new ArrayList<>();

    @Column(name = "published_at", nullable = false)
    @Convert(converter = KstInstantAttributeConverter.class)
    private Instant publishedAt;

    @Column(name = "created_by", nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID createdBy;

    @Column(name = "updated_by", nullable = false, columnDefinition = "BINARY(16)")
    private UUID updatedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Convert(converter = KstInstantAttributeConverter.class)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    @Convert(converter = KstInstantAttributeConverter.class)
    private Instant updatedAt;

    protected FestivalPerformance() { }

    FestivalPerformance(PerformanceCategory category, String teamName, List<String> memberNames,
                        Instant startsAt, Instant endsAt, String description, List<String> links,
                        Instant publishedAt, UUID actorUuid) {
        updateDetails(category, teamName, memberNames, startsAt, endsAt, description, links,
                publishedAt, actorUuid);
        this.createdBy = actorUuid;
    }

    void updateDetails(PerformanceCategory category, String teamName, List<String> memberNames,
                       Instant startsAt, Instant endsAt, String description, List<String> links,
                       Instant publishedAt, UUID actorUuid) {
        this.category = category;
        this.teamName = teamName;
        this.memberNames.clear();
        this.memberNames.addAll(memberNames);
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.description = description;
        this.links.clear();
        this.links.addAll(links);
        this.publishedAt = publishedAt;
        this.updatedBy = actorUuid;
    }

    void addMedia(PerformanceMedia item) {
        item.attachTo(this);
        media.add(item);
    }

    void removeMedia(PerformanceMedia item) {
        media.remove(item);
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
    public PerformanceCategory getCategory() { return category; }
    public String getTeamName() { return teamName; }
    public List<String> getMemberNames() { return List.copyOf(memberNames); }
    public Instant getStartsAt() { return startsAt; }
    public Instant getEndsAt() { return endsAt; }
    public String getDescription() { return description; }
    public List<String> getLinks() { return List.copyOf(links); }
    public List<PerformanceMedia> getMedia() { return List.copyOf(media); }
    public Instant getPublishedAt() { return publishedAt; }
    public UUID getCreatedBy() { return createdBy; }
    public UUID getUpdatedBy() { return updatedBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
