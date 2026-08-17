package org.syu_likelion.Festa_2026.booth;

import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.syu_likelion.Festa_2026.user.FestivalUser;

public interface FestivalBoothRepository extends JpaRepository<FestivalBooth, Long> {
    @EntityGraph(attributePaths = "media")
    List<FestivalBooth> findAllByOrderByNameAsc();
    List<FestivalBooth> findAllByStampEnabledTrueOrderByNameAsc();
    List<FestivalBooth> findAllByStampEnabledTrueAndManagersUserUuidOrderByNameAsc(java.util.UUID userUuid);
    boolean existsByIdAndManagersUserUuid(Long id, java.util.UUID userUuid);
    long countByManagersContains(FestivalUser manager);
}
