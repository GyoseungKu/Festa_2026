package org.syu_likelion.Festa_2026.booth;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.syu_likelion.Festa_2026.user.FestivalUser;

@Entity
@Table(name = "festival_booth_favorites", uniqueConstraints =
        @UniqueConstraint(name = "uk_booth_favorite_user", columnNames = {"booth_id", "festival_user_id"}))
public class BoothFavorite {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booth_id", nullable = false)
    private FestivalBooth booth;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "festival_user_id", nullable = false)
    private FestivalUser user;
    protected BoothFavorite() { }
    BoothFavorite(FestivalBooth booth, FestivalUser user) { this.booth = booth; this.user = user; }
}
