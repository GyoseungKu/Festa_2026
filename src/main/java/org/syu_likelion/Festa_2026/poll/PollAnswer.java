package org.syu_likelion.Festa_2026.poll;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "festival_poll_answers")
public class PollAnswer {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "submission_id", nullable = false)
    private PollSubmission submission;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "question_id", nullable = false)
    private PollQuestion question;
    @Column(name = "text_value", columnDefinition = "TEXT") private String textValue;
    @ManyToMany
    @JoinTable(name = "festival_poll_answer_options",
            joinColumns = @JoinColumn(name = "answer_id"), inverseJoinColumns = @JoinColumn(name = "option_id"))
    private List<PollOption> selectedOptions = new ArrayList<>();

    protected PollAnswer() { }
    PollAnswer(PollQuestion question, String textValue, List<PollOption> selectedOptions) {
        this.question = question; this.textValue = textValue;
        if (selectedOptions != null) this.selectedOptions.addAll(selectedOptions);
    }
    void attachTo(PollSubmission submission) { this.submission = submission; }
    public PollQuestion getQuestion() { return question; }
    public String getTextValue() { return textValue; }
    public List<PollOption> getSelectedOptions() { return List.copyOf(selectedOptions); }
}
