package org.syu_likelion.Festa_2026.performance;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FestivalPerformanceRepository extends JpaRepository<FestivalPerformance, Long> {
    List<FestivalPerformance> findByPublishedAtLessThanEqualOrderByStartsAtAsc(Instant publishedAt);
    Optional<FestivalPerformance> findByIdAndPublishedAtLessThanEqual(Long id, Instant publishedAt);
    List<FestivalPerformance> findAllByOrderByStartsAtAsc();
}
