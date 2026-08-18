package org.syu_likelion.Festa_2026.poll;

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
import jakarta.persistence.Table;

@Entity
@Table(name = "festival_poll_question_media")
public class PollQuestionMedia {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false) private PollQuestion question;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 10) private PollQuestionMediaKind kind;
    @Column(name = "media_url", nullable = false, length = 2048) private String url;
    @Column(name = "storage_key", nullable = false, length = 1024) private String storageKey;
    @Column(name = "original_filename", nullable = false, length = 255) private String originalFilename;
    @Column(name = "display_order", nullable = false) private int displayOrder;

    protected PollQuestionMedia() { }
    PollQuestionMedia(PollQuestionMediaKind kind, String url, String storageKey,
                      String originalFilename, int displayOrder) {
        this.kind = kind; this.url = url; this.storageKey = storageKey;
        this.originalFilename = originalFilename; this.displayOrder = displayOrder;
    }
    void attachTo(PollQuestion question) { this.question = question; }
    void setDisplayOrder(int value) { displayOrder = value; }
    public Long getId() { return id; }
    public PollQuestionMediaKind getKind() { return kind; }
    public String getUrl() { return url; }
    public String getStorageKey() { return storageKey; }
    public String getOriginalFilename() { return originalFilename; }
    public int getDisplayOrder() { return displayOrder; }
}
