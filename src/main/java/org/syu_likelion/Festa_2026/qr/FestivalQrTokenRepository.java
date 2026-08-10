package org.syu_likelion.Festa_2026.qr;

import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FestivalQrTokenRepository extends JpaRepository<FestivalQrToken, String> {
    long deleteByExpiresAtLessThanEqual(Instant instant);
}
