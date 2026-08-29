package org.syu_likelion.Festa_2026.bamboo;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface BambooMessageRepository extends JpaRepository<BambooMessage, Long> {

    /** 실시간 스트림. 신규 메시지와 상태 변경이 같은 커서로 흐른다. */
    List<BambooMessage> findBySeqGreaterThanOrderBySeqAsc(long seq, Pageable pageable);

    /** 과거 조회. 삭제·숨김 메시지는 반환하지 않는다. */
    List<BambooMessage> findByIdLessThanAndStatusOrderByIdDesc(Long id, BambooMessageStatus status,
                                                               Pageable pageable);

    @Query("select coalesce(max(m.seq), 0) from BambooMessage m")
    long findMaxSeq();
}
