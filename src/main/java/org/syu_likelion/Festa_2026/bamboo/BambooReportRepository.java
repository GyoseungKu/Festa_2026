package org.syu_likelion.Festa_2026.bamboo;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BambooReportRepository extends JpaRepository<BambooReport, Long> {
    boolean existsByMessageIdAndUserUuid(Long messageId, UUID userUuid);
    long countByMessageId(Long messageId);

    /** 목록 한 페이지의 신고 사유를 한 번에 가져와 화면에서 묶는다. */
    List<BambooReport> findByMessageIdIn(Collection<Long> messageIds);
}
