package org.syu_likelion.Festa_2026.poll;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.syu_likelion.Festa_2026.performance.KstInstantAttributeConverter;

@Entity
@Table(name = "festival_poll_submissions", uniqueConstraints =
        @UniqueConstraint(name = "uk_poll_single_submission", columnNames = {"poll_id", "single_vote_key"}))
public class PollSubmission {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional = false, fetch = jakarta.persistence.FetchType.LAZY)
    @JoinColumn(name = "poll_id", nullable = false) private FestivalPoll poll;
    @Column(name = "user_uuid", nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID userUuid;
    @Column(name = "single_vote_key", columnDefinition = "BINARY(16)") private UUID singleVoteKey;
    @OneToMany(mappedBy = "submission", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC") private List<PollAnswer> answers = new ArrayList<>();
    @Column(name = "submitted_at", nullable = false, updatable = false)
    @Convert(converter = KstInstantAttributeConverter.class) private Instant submittedAt;

    protected PollSubmission() { }
    PollSubmission(FestivalPoll poll, UUID userUuid, Instant submittedAt) {
        this.poll = poll; this.userUuid = userUuid;
        this.singleVoteKey = poll.isAllowMultipleSubmissions() ? null : userUuid;
        this.submittedAt = submittedAt;
    }
    void addAnswer(PollAnswer answer) { answer.attachTo(this); answers.add(answer); }
    public Long getId() { return id; }
    public UUID getUserUuid() { return userUuid; }
    public List<PollAnswer> getAnswers() { return List.copyOf(answers); }
    public Instant getSubmittedAt() { return submittedAt; }
}
