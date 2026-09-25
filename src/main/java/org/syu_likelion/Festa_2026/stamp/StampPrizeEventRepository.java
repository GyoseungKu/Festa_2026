package org.syu_likelion.Festa_2026.stamp;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StampPrizeEventRepository extends JpaRepository<StampPrizeEvent, Long> {
    boolean existsByPrizeId(Long prizeId);
    Page<StampPrizeEvent> findByPrizeId(Long prizeId, Pageable pageable);
}
