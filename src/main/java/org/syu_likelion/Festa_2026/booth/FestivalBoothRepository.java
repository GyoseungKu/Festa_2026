package org.syu_likelion.Festa_2026.booth;

import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.syu_likelion.Festa_2026.user.FestivalUser;

public interface FestivalBoothRepository extends JpaRepository<FestivalBooth, Long> {
    @EntityGraph(attributePaths = "media")
    List<FestivalBooth> findAllByOrderByNameAsc();
    List<FestivalBooth> findAllByStampEnabledTrueOrderByNameAsc();
    List<FestivalBooth> findAllByStampEnabledTrueAndManagersUserUuidOrderByNameAsc(java.util.UUID userUuid);
    boolean existsByIdAndManagersUserUuid(Long id, java.util.UUID userUuid);
    long countByManagersContains(FestivalUser manager);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select booth from FestivalBooth booth where booth.id = :id")
    Optional<FestivalBooth> findByIdForUpdate(@Param("id") Long id);
}
