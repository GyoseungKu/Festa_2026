package org.syu_likelion.Festa_2026.lostitem;

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
@Table(name = "lost_item_notice_images")
public class LostItemImage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "notice_id", nullable = false)
    private LostItemNotice notice;

    @Column(nullable = false, length = 2048)
    private String url;

    @Column(name = "storage_key", nullable = false, length = 512)
    private String storageKey;

    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    protected LostItemImage() { }

    LostItemImage(String url, String storageKey, String originalFilename) {
        this.url = url;
        this.storageKey = storageKey;
        this.originalFilename = originalFilename;
    }

    void attachTo(LostItemNotice notice) {
        this.notice = notice;
    }

    public Long getId() { return id; }
    public String getUrl() { return url; }
    public String getStorageKey() { return storageKey; }
    public String getOriginalFilename() { return originalFilename; }
}
