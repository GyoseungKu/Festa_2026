package org.syu_likelion.Festa_2026.sponsor;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.syu_likelion.Festa_2026.booth.FestivalBooth;

@Entity
@Table(name = "festival_sponsors")
public class Sponsor {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;
    @Column(nullable = false, length = 100)
    String name;
    @Column(nullable = false, length = 2000)
    String description;
    @Column(nullable = false, length = 2048)
    String imageUrl;
    @Column(nullable = false, length = 1024)
    String storageKey;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booth_id")
    @OnDelete(action = OnDeleteAction.SET_NULL)
    FestivalBooth booth;
    @Column(nullable = false, updatable = false)
    Instant createdAt;
    @Column(nullable = false)
    Instant updatedAt;
    @Column(nullable = false, updatable = false)
    UUID createdBy;
    @Column(nullable = false)
    UUID updatedBy;
    @Version
    Long version;
    protected Sponsor() { }
}

