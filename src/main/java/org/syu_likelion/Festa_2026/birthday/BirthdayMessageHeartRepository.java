package org.syu_likelion.Festa_2026.birthday;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BirthdayMessageHeartRepository extends JpaRepository<BirthdayMessageHeart, Long> {
    boolean existsByMessage_IdAndUserUuid(Long messageId, UUID userUuid);
    int deleteByMessage_IdAndUserUuid(Long messageId, UUID userUuid);
    Page<BirthdayMessageHeart> findByMessage_Id(Long messageId, Pageable pageable);
    List<BirthdayMessageHeart> findByMessage_IdInAndUserUuid(List<Long> messageIds, UUID userUuid);
}
