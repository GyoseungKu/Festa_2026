package org.syu_likelion.Festa_2026.poll;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PollQuestionMediaRepository extends JpaRepository<PollQuestionMedia, Long> {
    Optional<PollQuestionMedia> findByIdAndQuestionIdAndQuestionPollId(Long id, Long questionId, Long pollId);
}
