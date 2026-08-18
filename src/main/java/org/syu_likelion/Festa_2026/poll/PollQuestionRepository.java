package org.syu_likelion.Festa_2026.poll;

import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface PollQuestionRepository extends JpaRepository<PollQuestion, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PollQuestion> findByIdAndPollId(Long id, Long pollId);
}
