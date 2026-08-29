package org.syu_likelion.Festa_2026.bamboo;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BambooReportRepository extends JpaRepository<BambooReport, Long> {
    boolean existsByMessageIdAndUserUuid(Long messageId, UUID userUuid);
    long countByMessageId(Long messageId);
}
