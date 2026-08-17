package org.syu_likelion.Festa_2026.booth;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.syu_likelion.Festa_2026.user.FestivalUser;

@Entity
@Table(name = "festival_booths")
public class FestivalBooth {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(nullable = false, precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, length = 150)
    private String operator;

    @Lob @Column(nullable = false)
    private String description;

    @Column(name = "opens_at", nullable = false)
    private LocalTime opensAt;

    @Column(name = "closes_at", nullable = false)
    private LocalTime closesAt;

    @Column(name = "stamp_enabled", nullable = false, columnDefinition = "boolean default false")
    private boolean stampEnabled;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "festival_booth_managers",
            joinColumns = @JoinColumn(name = "booth_id"),
            inverseJoinColumns = @JoinColumn(name = "festival_user_id"))
    private Set<FestivalUser> managers = new LinkedHashSet<>();

    @OneToMany(mappedBy = "booth", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderColumn(name = "display_order")
    private List<BoothMedia> media = new ArrayList<>();

    @Column(name = "created_by", nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID createdBy;
    @Column(name = "updated_by", nullable = false, columnDefinition = "BINARY(16)")
    private UUID updatedBy;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected FestivalBooth() { }

    FestivalBooth(BigDecimal latitude, BigDecimal longitude, String name, String operator,
                  String description, LocalTime opensAt, LocalTime closesAt,
                  boolean stampEnabled, Set<FestivalUser> managers, UUID actorUuid) {
        update(latitude, longitude, name, operator, description, opensAt, closesAt,
                stampEnabled, managers, actorUuid);
        createdBy = actorUuid;
    }

    void update(BigDecimal latitude, BigDecimal longitude, String name, String operator,
                String description, LocalTime opensAt, LocalTime closesAt,
                boolean stampEnabled, Set<FestivalUser> managers, UUID actorUuid) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.name = name;
        this.operator = operator;
        this.description = description;
        this.opensAt = opensAt;
        this.closesAt = closesAt;
        this.stampEnabled = stampEnabled;
        this.managers.clear();
        this.managers.addAll(managers);
        this.updatedBy = actorUuid;
    }

    void addMedia(BoothMedia item) { item.attachTo(this); media.add(item); }
    void removeMedia(BoothMedia item) { media.remove(item); }
    void reorderMedia(List<BoothMedia> ordered) {
        for (int target = 0; target < ordered.size(); target++) {
            int current = media.indexOf(ordered.get(target));
            if (current != target) java.util.Collections.swap(media, target, current);
        }
    }

    @PrePersist void prePersist() { createdAt = Instant.now(); updatedAt = createdAt; }
    @PreUpdate void preUpdate() { updatedAt = Instant.now(); }

    public Long getId() { return id; }
    public BigDecimal getLatitude() { return latitude; }
    public BigDecimal getLongitude() { return longitude; }
    public String getName() { return name; }
    public String getOperator() { return operator; }
    public String getDescription() { return description; }
    public LocalTime getOpensAt() { return opensAt; }
    public LocalTime getClosesAt() { return closesAt; }
    public boolean isStampEnabled() { return stampEnabled; }
    public Set<FestivalUser> getManagers() { return Set.copyOf(managers); }
    public List<BoothMedia> getMedia() { return List.copyOf(media); }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
