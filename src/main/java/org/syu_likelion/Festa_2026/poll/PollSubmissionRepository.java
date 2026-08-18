package org.syu_likelion.Festa_2026.poll;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface PollSubmissionRepository extends JpaRepository<PollSubmission, Long> {
    boolean existsByPollId(Long pollId);
    boolean existsByPollIdAndUserUuid(Long pollId, UUID userUuid);
    long countByPollId(Long pollId);
    long countByPollIdAndUserUuid(Long pollId, UUID userUuid);
    List<PollSubmission> findByPollIdOrderBySubmittedAtDesc(Long pollId);
    Page<PollSubmission> findByPollId(Long pollId, Pageable pageable);
    List<PollSubmission> findByPollIdAndUserUuidOrderBySubmittedAtDesc(Long pollId, UUID userUuid);
    void deleteByPollId(Long pollId);
}
