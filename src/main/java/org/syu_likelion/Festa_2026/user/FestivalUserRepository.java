package org.syu_likelion.Festa_2026.user;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FestivalUserRepository extends JpaRepository<FestivalUser, Long> {
    Optional<FestivalUser> findByUserUuid(UUID userUuid);
    List<FestivalUser> findAllByUserUuidIn(Collection<UUID> userUuids);
}
