package org.syu_likelion.Festa_2026.poll;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "festival_poll_options")
public class PollOption {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "question_id", nullable = false)
    private PollQuestion question;
    @Column(name = "option_text", nullable = false, length = 200) private String text;
    @Column(name = "display_order", nullable = false) private int displayOrder;
    @Column(name = "image_url", length = 2048) private String imageUrl;
    @Column(name = "image_storage_key", length = 1024) private String imageStorageKey;
    @Column(name = "image_original_filename", length = 255) private String imageOriginalFilename;

    protected PollOption() { }
    PollOption(String text, int displayOrder) { this.text = text; this.displayOrder = displayOrder; }
    void attachTo(PollQuestion question) { this.question = question; }
    void setImage(String url, String storageKey, String originalFilename) {
        imageUrl = url; imageStorageKey = storageKey; imageOriginalFilename = originalFilename;
    }
    public Long getId() { return id; }
    public String getText() { return text; }
    public int getDisplayOrder() { return displayOrder; }
    public String getImageUrl() { return imageUrl; }
    public String getImageStorageKey() { return imageStorageKey; }
    public String getImageOriginalFilename() { return imageOriginalFilename; }
}
