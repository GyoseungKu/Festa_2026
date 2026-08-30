package org.syu_likelion.Festa_2026.user;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface SchoolVerificationRequestRepository extends JpaRepository<SchoolVerificationRequest, Long> {
    Optional<SchoolVerificationRequest> findByUserUuid(UUID userUuid);
    void deleteByUserUuid(UUID userUuid);
    List<SchoolVerificationRequest> findAllByOrderByRequestedAtAsc();
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select request from SchoolVerificationRequest request where request.id = :id")
    Optional<SchoolVerificationRequest> findByIdForUpdate(Long id);
}
