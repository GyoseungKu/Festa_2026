package org.syu_likelion.Festa_2026.performance;

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
@Table(name = "festival_performance_media")
public class PerformanceMedia {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "performance_id", nullable = false)
    private FestivalPerformance performance;

    @Enumerated(EnumType.STRING)
    @Column(name = "media_kind", nullable = false, length = 16)
    private PerformanceMediaKind kind;

    @Enumerated(EnumType.STRING)
    @Column(name = "media_source", nullable = false, length = 16)
    private PerformanceMediaSource source;

    @Column(name = "media_url", nullable = false, length = 2048)
    private String url;

    @Column(name = "storage_key", length = 512)
    private String storageKey;

    @Column(name = "original_filename", length = 255)
    private String originalFilename;

    protected PerformanceMedia() { }

    private PerformanceMedia(PerformanceMediaKind kind, PerformanceMediaSource source,
                             String url, String storageKey, String originalFilename) {
        this.kind = kind;
        this.source = source;
        this.url = url;
        this.storageKey = storageKey;
        this.originalFilename = originalFilename;
    }

    static PerformanceMedia linked(PerformanceMediaKind kind, String url) {
        return new PerformanceMedia(kind, PerformanceMediaSource.LINK, url, null, null);
    }

    static PerformanceMedia uploaded(PerformanceMediaKind kind, String url,
                                     String storageKey, String originalFilename) {
        return new PerformanceMedia(kind, PerformanceMediaSource.UPLOAD, url, storageKey, originalFilename);
    }

    void attachTo(FestivalPerformance performance) {
        this.performance = performance;
    }

    public Long getId() { return id; }
    public PerformanceMediaKind getKind() { return kind; }
    public PerformanceMediaSource getSource() { return source; }
    public String getUrl() { return url; }
    public String getStorageKey() { return storageKey; }
    public String getOriginalFilename() { return originalFilename; }
}
