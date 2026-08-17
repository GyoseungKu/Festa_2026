package org.syu_likelion.Festa_2026.booth;

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
@Table(name = "festival_booth_media")
public class BoothMedia {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booth_id", nullable = false)
    private FestivalBooth booth;
    @Enumerated(EnumType.STRING)
    @Column(name = "media_kind", nullable = false, length = 16)
    private BoothMediaKind kind;
    @Column(name = "media_url", nullable = false, length = 2048)
    private String url;
    @Column(name = "storage_key", nullable = false, length = 512)
    private String storageKey;
    @Column(name = "original_filename", length = 255)
    private String originalFilename;
    @Column(name = "is_representative", nullable = false)
    private boolean representative;

    protected BoothMedia() { }
    BoothMedia(BoothMediaKind kind, String url, String storageKey, String originalFilename) {
        this.kind = kind; this.url = url; this.storageKey = storageKey; this.originalFilename = originalFilename;
    }
    void attachTo(FestivalBooth booth) { this.booth = booth; }
    void setRepresentative(boolean representative) { this.representative = representative; }
    public Long getId() { return id; }
    public BoothMediaKind getKind() { return kind; }
    public String getUrl() { return url; }
    public String getStorageKey() { return storageKey; }
    public String getOriginalFilename() { return originalFilename; }
    public boolean isRepresentative() { return representative; }
}
