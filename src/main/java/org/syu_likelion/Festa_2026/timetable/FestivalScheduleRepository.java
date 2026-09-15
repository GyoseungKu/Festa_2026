package org.syu_likelion.Festa_2026.timetable;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FestivalScheduleRepository extends JpaRepository<FestivalSchedule, Long> {
    @EntityGraph(attributePaths = "performance")
    List<FestivalSchedule> findAllByOrderByStartsAtAscIdAsc();

    @Override
    @EntityGraph(attributePaths = "performance")
    Optional<FestivalSchedule> findById(Long id);
}
