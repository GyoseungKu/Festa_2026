package org.syu_likelion.Festa_2026.stamp;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StampEventRepository extends JpaRepository<StampEvent, Long> {
    List<StampEvent> findAllByBoothIdOrderByOccurredAtDesc(Long boothId);
    boolean existsByTargetUserUuidAndAction(UUID targetUserUuid, StampAction action);
}
