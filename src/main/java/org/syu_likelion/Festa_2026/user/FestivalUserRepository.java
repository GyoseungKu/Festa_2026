package org.syu_likelion.Festa_2026.user;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

public interface FestivalUserRepository extends JpaRepository<FestivalUser, Long> {
    Optional<FestivalUser> findByUserUuid(UUID userUuid);
    List<FestivalUser> findAllByUserUuidIn(Collection<UUID> userUuids);
    @Query("select user.userUuid from FestivalUser user order by user.id")
    List<UUID> findAllUserUuids();
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select user from FestivalUser user where user.userUuid = :userUuid")
    Optional<FestivalUser> findByUserUuidForUpdate(UUID userUuid);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select user from FestivalUser user where user.managementRole = :managementRole order by user.id")
    List<FestivalUser> findAllByManagementRoleForUpdate(FestivalRole managementRole);
}
