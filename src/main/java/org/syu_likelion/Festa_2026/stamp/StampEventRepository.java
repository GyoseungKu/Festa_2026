package org.syu_likelion.Festa_2026.stamp;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StampEventRepository extends JpaRepository<StampEvent, Long> {
    Page<StampEvent> findAllByBoothId(Long boothId, Pageable pageable);
    boolean existsByTargetUserUuidAndAction(UUID targetUserUuid, StampAction action);
}
