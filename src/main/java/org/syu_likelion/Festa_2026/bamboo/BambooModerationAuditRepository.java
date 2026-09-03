package org.syu_likelion.Festa_2026.bamboo;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BambooModerationAuditRepository extends JpaRepository<BambooModerationAudit, Long> {
    Page<BambooModerationAudit> findAllByOrderByOccurredAtDescIdDesc(Pageable pageable);
}
