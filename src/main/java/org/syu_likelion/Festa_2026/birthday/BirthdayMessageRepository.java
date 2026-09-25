package org.syu_likelion.Festa_2026.birthday;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BirthdayMessageRepository extends JpaRepository<BirthdayMessage, Long> {
    Optional<BirthdayMessage> findByAuthorUuid(UUID authorUuid);
    Page<BirthdayMessage> findAll(Pageable pageable);

    @Query("select m.id from BirthdayMessage m order by m.id")
    java.util.List<Long> findAllIdsForShuffle();

    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from BirthdayMessage m where m.id = :id")
    Optional<BirthdayMessage> findForUpdateById(@Param("id") Long id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update BirthdayMessage m set m.heartCount = m.heartCount + 1 where m.id = :id")
    int incrementHeartCount(@Param("id") Long id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update BirthdayMessage m set m.heartCount = case when m.heartCount > 0 then m.heartCount - 1 else 0 end where m.id = :id")
    int decrementHeartCount(@Param("id") Long id);
}
