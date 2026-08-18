package org.syu_likelion.Festa_2026.poll;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Entity
@Table(name = "festival_poll_questions")
public class PollQuestion {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "poll_id", nullable = false)
    private FestivalPoll poll;
    @Column(name = "question_text", nullable = false, length = 500) private String text;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private PollQuestionType type;
    @Column(nullable = false) private boolean required;
    @Column(name = "display_order", nullable = false) private int displayOrder;
    @OneToMany(mappedBy = "question", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("displayOrder ASC, id ASC") private List<PollOption> options = new ArrayList<>();
    @OneToMany(mappedBy = "question", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("displayOrder ASC, id ASC") private List<PollQuestionMedia> media = new ArrayList<>();

    protected PollQuestion() { }
    PollQuestion(String text, PollQuestionType type, boolean required, int displayOrder) {
        this.text = text; this.type = type; this.required = required; this.displayOrder = displayOrder;
    }
    void attachTo(FestivalPoll poll) {
        this.poll = poll;
        options.forEach(option -> option.attachTo(this));
        media.forEach(item -> item.attachTo(this));
    }
    void addOption(PollOption option) { option.attachTo(this); options.add(option); }
    void addMedia(PollQuestionMedia item) { item.attachTo(this); media.add(item); }
    void removeMedia(PollQuestionMedia item) { media.remove(item); }
    void reorderMedia(List<PollQuestionMedia> ordered) {
        for (int index = 0; index < ordered.size(); index++) {
            PollQuestionMedia item = ordered.get(index);
            item.setDisplayOrder(index);
            item.attachTo(this);
        }
        media.sort(Comparator.comparingInt(PollQuestionMedia::getDisplayOrder));
    }
    public Long getId() { return id; }
    public String getText() { return text; }
    public PollQuestionType getType() { return type; }
    public boolean isRequired() { return required; }
    public int getDisplayOrder() { return displayOrder; }
    public List<PollOption> getOptions() { return List.copyOf(options); }
    public List<PollQuestionMedia> getMedia() { return List.copyOf(media); }
}
