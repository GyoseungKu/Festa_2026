package org.syu_likelion.Festa_2026.bamboo;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BambooNicknameRepository extends JpaRepository<BambooNickname, UUID> {
    boolean existsByNicknameKey(String nicknameKey);
}
