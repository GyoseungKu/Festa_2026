package org.syu_likelion.Festa_2026.notice;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NoticeRepository extends JpaRepository<Notice, Long> {
    Page<Notice> findByBannerTrue(Pageable pageable);

    @EntityGraph(attributePaths = "attachments")
    @Query("select n from Notice n where n.id = :id")
    Optional<Notice> findWithAttachmentsById(@Param("id") Long id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Notice n set n.viewCount = n.viewCount + 1 where n.id = :id")
    int incrementViewCount(@Param("id") Long id);
}
