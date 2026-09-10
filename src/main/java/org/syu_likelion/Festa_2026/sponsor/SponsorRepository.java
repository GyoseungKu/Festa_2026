package org.syu_likelion.Festa_2026.sponsor;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

public interface SponsorRepository extends JpaRepository<Sponsor, Long> {
    @EntityGraph(attributePaths = "booth")
    List<Sponsor> findAllByOrderByIdAsc();
}

