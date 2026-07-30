package org.syu_likelion.Feata_2026.qr;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JpaQrTokenStore implements QrTokenStore {
    private final FestivalQrTokenRepository repository;
    private final Clock clock;

    public JpaQrTokenStore(FestivalQrTokenRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void save(String token, UUID userUuid, Duration ttl) {
        Instant now = clock.instant();
        repository.save(new FestivalQrToken(hash(token), userUuid, now.plus(ttl), now));
    }

    @Override
    @Transactional
    public Optional<UUID> findUserUuid(String token) {
        Optional<FestivalQrToken> stored = repository.findById(hash(token));
        if (stored.isEmpty()) return Optional.empty();
        if (stored.get().isExpiredAt(clock.instant())) {
            repository.delete(stored.get());
            return Optional.empty();
        }
        return Optional.of(stored.get().getUserUuid());
    }

    @Scheduled(fixedDelayString = "${qr.cleanup-interval-ms:60000}")
    @Transactional
    public void deleteExpiredTokens() {
        repository.deleteByExpiresAtLessThanEqual(clock.instant());
    }

    private String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
