package org.syu_likelion.Festa_2026.poll;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PollOptionRepository extends JpaRepository<PollOption, Long> {
    Optional<PollOption> findByIdAndQuestionPollId(Long id, Long pollId);
}
