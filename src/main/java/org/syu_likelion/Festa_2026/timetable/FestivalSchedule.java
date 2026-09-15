package org.syu_likelion.Festa_2026.timetable;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.syu_likelion.Festa_2026.performance.FestivalPerformance;
import org.syu_likelion.Festa_2026.performance.KstInstantAttributeConverter;

@Entity
@Table(name = "festival_schedules", indexes = {
        @Index(name = "idx_schedule_starts_at", columnList = "starts_at"),
        @Index(name = "idx_schedule_performance", columnList = "performance_id")
})
public class FestivalSchedule {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 150)
    private String title;
    @Column(name = "starts_at", nullable = false)
    @Convert(converter = KstInstantAttributeConverter.class)
    private Instant startsAt;
    @Column(name = "ends_at", nullable = false)
    @Convert(converter = KstInstantAttributeConverter.class)
    private Instant endsAt;
    @Column(name = "published_at", nullable = false)
    @Convert(converter = KstInstantAttributeConverter.class)
    private Instant publishedAt;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "performance_id", foreignKey = @ForeignKey(name = "fk_schedule_performance"))
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private FestivalPerformance performance;
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

    protected FestivalSchedule() { }

    FestivalSchedule(String title, Instant startsAt, Instant endsAt, Instant publishedAt,
                     FestivalPerformance performance, UUID actor) {
        update(title, startsAt, endsAt, publishedAt, performance, actor);
        createdBy = actor;
    }

    void update(String title, Instant startsAt, Instant endsAt, Instant publishedAt,
                FestivalPerformance performance, UUID actor) {
        this.title = title;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.publishedAt = publishedAt;
        this.performance = performance;
        updatedBy = actor;
    }

    @PrePersist
    void created() { createdAt = Instant.now(); updatedAt = createdAt; }
    @PreUpdate
    void updated() { updatedAt = Instant.now(); }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public Instant getStartsAt() { return startsAt; }
    public Instant getEndsAt() { return endsAt; }
    public Instant getPublishedAt() { return publishedAt; }
    public FestivalPerformance getPerformance() { return performance; }
}
