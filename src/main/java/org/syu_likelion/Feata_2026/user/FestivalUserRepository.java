package org.syu_likelion.Feata_2026.user;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FestivalUserRepository extends JpaRepository<FestivalUser, Long> {
    Optional<FestivalUser> findByUserUuid(UUID userUuid);
}
