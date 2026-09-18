package org.syu_likelion.Festa_2026.poll;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.syu_likelion.Festa_2026.performance.KstInstantAttributeConverter;

@Entity
@Table(name = "festival_polls", indexes = {
        @Index(name = "idx_poll_published_at", columnList = "published_at"),
        @Index(name = "idx_poll_period", columnList = "starts_at,ends_at")
})
public class FestivalPoll {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 200) private String title;
    @Column(nullable = false, columnDefinition = "TEXT") private String description;
    @Column(nullable = false) private boolean anonymous;
    @Column(name = "allow_multiple_submissions", nullable = false) private boolean allowMultipleSubmissions;
    @Column(name = "published_at", nullable = false) @Convert(converter = KstInstantAttributeConverter.class)
    private Instant publishedAt;
    @Column(name = "starts_at", nullable = false) @Convert(converter = KstInstantAttributeConverter.class)
    private Instant startsAt;
    @Column(name = "ends_at", nullable = false) @Convert(converter = KstInstantAttributeConverter.class)
    private Instant endsAt;
    @Column(name = "result_published_at") @Convert(converter = KstInstantAttributeConverter.class)
    private Instant resultPublishedAt;
    @Column(name = "closed_at") @Convert(converter = KstInstantAttributeConverter.class)
    private Instant closedAt;
    @OneToMany(mappedBy = "poll", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("displayOrder ASC, id ASC")
    private List<PollQuestion> questions = new ArrayList<>();
    @Column(name = "created_by", nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID createdBy;
    @Column(name = "updated_by", nullable = false, columnDefinition = "BINARY(16)") private UUID updatedBy;
    @Column(name = "created_at", nullable = false, updatable = false) @Convert(converter = KstInstantAttributeConverter.class)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false) @Convert(converter = KstInstantAttributeConverter.class)
    private Instant updatedAt;

    protected FestivalPoll() { }

    FestivalPoll(String title, String description, boolean anonymous, boolean allowMultipleSubmissions,
                 Instant publishedAt, Instant startsAt, Instant endsAt, Instant resultPublishedAt, UUID actor) {
        updateDefinition(title, description, anonymous, allowMultipleSubmissions, publishedAt, startsAt,
                endsAt, resultPublishedAt, actor);
        createdBy = actor;
    }

    void updateDefinition(String title, String description, boolean anonymous, boolean allowMultipleSubmissions,
                          Instant publishedAt, Instant startsAt, Instant endsAt, Instant resultPublishedAt,
                          UUID actor) {
        this.title = title; this.description = description; this.anonymous = anonymous;
        this.allowMultipleSubmissions = allowMultipleSubmissions; this.publishedAt = publishedAt;
        this.startsAt = startsAt; this.endsAt = endsAt; this.resultPublishedAt = resultPublishedAt;
        this.updatedBy = actor;
    }

    void updateSettings(String title, String description, Instant endsAt, Instant resultPublishedAt, UUID actor) {
        this.title = title; this.description = description; this.endsAt = endsAt;
        this.resultPublishedAt = resultPublishedAt; this.updatedBy = actor;
    }

    void replaceQuestions(List<PollQuestion> replacements) {
        questions.clear();
        replacements.forEach(question -> { question.attachTo(this); questions.add(question); });
    }

    void close(Instant when, UUID actor) { closedAt = when; updatedBy = actor; }

    @PrePersist void prePersist() { Instant now = Instant.now(); createdAt = now; updatedAt = now; }
    @PreUpdate void preUpdate() { updatedAt = Instant.now(); }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public boolean isAnonymous() { return anonymous; }
    public boolean isAllowMultipleSubmissions() { return allowMultipleSubmissions; }
    public Instant getPublishedAt() { return publishedAt; }
    public Instant getStartsAt() { return startsAt; }
    public Instant getEndsAt() { return endsAt; }
    public Instant getResultPublishedAt() { return resultPublishedAt; }
    public Instant getClosedAt() { return closedAt; }
    public List<PollQuestion> getQuestions() { return List.copyOf(questions); }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
