package org.syu_likelion.Festa_2026.poll;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FestivalPollRepository extends JpaRepository<FestivalPoll, Long> {
    List<FestivalPoll> findByPublishedAtLessThanEqualOrderByStartsAtAsc(Instant now);
    Optional<FestivalPoll> findByIdAndPublishedAtLessThanEqual(Long id, Instant now);
    List<FestivalPoll> findAllByOrderByCreatedAtDesc();
}
