package org.syu_likelion.Festa_2026.booth;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BoothFavoriteRepository extends JpaRepository<BoothFavorite, Long> {
    Optional<BoothFavorite> findByBoothIdAndUserUserUuid(Long boothId, UUID userUuid);
    boolean existsByBoothIdAndUserUserUuid(Long boothId, UUID userUuid);
    @Query("select f.booth.id from BoothFavorite f where f.user.userUuid = :userUuid")
    List<Long> findBoothIdsByUserUuid(@Param("userUuid") UUID userUuid);
    @EntityGraph(attributePaths = "media")
    @Query("select f.booth from BoothFavorite f where f.user.userUuid = :userUuid order by f.booth.name asc")
    List<FestivalBooth> findBoothsByUserUuid(@Param("userUuid") UUID userUuid);
    void deleteAllByBoothId(Long boothId);
}
