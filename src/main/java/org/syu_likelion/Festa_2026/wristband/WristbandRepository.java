package org.syu_likelion.Festa_2026.wristband;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WristbandRepository extends JpaRepository<Wristband, Long> {
    Optional<Wristband> findBySubjectHash(String hash);
    Optional<Wristband> findByActiveUserUuid(UUID userUuid);
    java.util.List<Wristband> findByActiveUserUuidIn(java.util.Collection<UUID> userUuids);
    java.util.List<Wristband> findBySubjectHashInAndIssuedTrue(java.util.Collection<String> hashes);
    long countByIssuedTrue();
    org.springframework.data.domain.Page<Wristband> findByIssued(boolean issued, org.springframework.data.domain.Pageable pageable);
}
