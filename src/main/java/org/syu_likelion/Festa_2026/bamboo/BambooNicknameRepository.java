package org.syu_likelion.Festa_2026.bamboo;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BambooNicknameRepository extends JpaRepository<BambooNickname, UUID> {
    boolean existsByNicknameKey(String nicknameKey);
    Optional<BambooNickname> findByNicknameKey(String nicknameKey);
    Page<BambooNickname> findAllByOrderByNicknameAsc(Pageable pageable);
    Page<BambooNickname> findByNicknameContainingIgnoreCaseOrderByNicknameAsc(String nickname, Pageable pageable);
}
