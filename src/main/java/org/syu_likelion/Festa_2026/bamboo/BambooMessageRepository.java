package org.syu_likelion.Festa_2026.bamboo;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BambooMessageRepository extends JpaRepository<BambooMessage, Long> {

    /** 실시간 스트림. 신규 메시지와 상태 변경이 같은 커서로 흐른다. */
    List<BambooMessage> findBySeqGreaterThanOrderBySeqAsc(long seq, Pageable pageable);

    /** 과거 조회. 삭제·숨김 메시지는 반환하지 않는다. */
    List<BambooMessage> findByIdLessThanAndStatusOrderByIdDesc(Long id, BambooMessageStatus status,
                                                               Pageable pageable);

    @Query("select coalesce(max(m.seq), 0) from BambooMessage m")
    long findMaxSeq();

    /** 신고된 메시지. 처리 여부와 무관하게 보여주고 화면에서 상태로 구분한다. */
    @Query("select m from BambooMessage m where m.reportCount > 0 order by m.reportCount desc, m.id desc")
    Page<BambooMessage> findReported(Pageable pageable);

    /** 신고 수는 동시에 올라갈 수 있으므로 원자적으로 증가시킨다. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update BambooMessage m set m.reportCount = m.reportCount + 1 where m.id = :id")
    int incrementReportCount(@Param("id") Long id);

    /** 닉네임 강제 변경 시 과거 메시지의 표시 이름을 함께 바꾼다. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update BambooMessage m set m.anonName = :anonName where m.userUuid = :userUuid")
    int renameAuthor(@Param("userUuid") java.util.UUID userUuid, @Param("anonName") String anonName);
}
