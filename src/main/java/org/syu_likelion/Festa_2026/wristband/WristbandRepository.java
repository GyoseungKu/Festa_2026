package org.syu_likelion.Festa_2026.wristband;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WristbandRepository extends JpaRepository<Wristband, Long> {
    Optional<Wristband> findBySubjectHash(String hash);
    Optional<Wristband> findByActiveUserUuid(UUID userUuid);
    long countByIssuedTrue();
}
