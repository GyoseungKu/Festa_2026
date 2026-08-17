package org.syu_likelion.Festa_2026.stamp;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BoothStampRepository extends JpaRepository<BoothStamp, Long> {
    Optional<BoothStamp> findByBoothIdAndUserUserUuid(Long boothId, UUID userUuid);
    boolean existsByBoothIdAndUserUserUuid(Long boothId, UUID userUuid);
    @EntityGraph(attributePaths = "booth")
    List<BoothStamp> findAllByUserUserUuidOrderByGrantedAtAsc(UUID userUuid);
    @EntityGraph(attributePaths = "user")
    List<BoothStamp> findAllByBoothIdOrderByGrantedAtDesc(Long boothId);
}
