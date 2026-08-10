package org.syu_likelion.Festa_2026.qr;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

public interface QrTokenStore {
    void save(String token, UUID userUuid, Duration ttl);
    Optional<UUID> findUserUuid(String token);
}
