package org.syu_likelion.Festa_2026.stamp;

import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface StampPrizeRepository extends JpaRepository<StampPrize, Long> {
    Optional<StampPrize> findByTargetUserUuid(UUID targetUserUuid);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from StampPrize p where p.targetUserUuid = :targetUserUuid")
    Optional<StampPrize> findByTargetUserUuidForUpdate(UUID targetUserUuid);
}
