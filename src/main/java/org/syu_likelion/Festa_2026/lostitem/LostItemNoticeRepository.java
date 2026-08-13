package org.syu_likelion.Festa_2026.lostitem;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LostItemNoticeRepository extends JpaRepository<LostItemNotice, Long> {
    Page<LostItemNotice> findByStatus(LostItemStatus status, Pageable pageable);
    java.util.List<LostItemNotice> findByStatus(LostItemStatus status, Sort sort);

    @EntityGraph(attributePaths = "images")
    @Query("select n from LostItemNotice n where n.id = :id")
    Optional<LostItemNotice> findWithImagesById(@Param("id") Long id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update LostItemNotice n set n.viewCount = n.viewCount + 1 where n.id = :id")
    int incrementViewCount(@Param("id") Long id);
}
