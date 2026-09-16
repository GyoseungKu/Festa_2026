package org.syu_likelion.Festa_2026.wristband;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WristbandEventRepository extends JpaRepository<WristbandEvent, Long> {
    Page<WristbandEvent> findByWristbandIdOrderByIdDesc(Long id, Pageable pageable);
}
