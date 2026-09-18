package org.syu_likelion.Festa_2026.performance;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FestivalPerformanceRepository extends JpaRepository<FestivalPerformance, Long> {
    List<FestivalPerformance> findAllByOrderByStartsAtAsc();
}
