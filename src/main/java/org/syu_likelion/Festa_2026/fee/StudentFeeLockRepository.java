package org.syu_likelion.Festa_2026.fee;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import java.util.Optional;
public interface StudentFeeLockRepository extends JpaRepository<StudentFeeLock, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select gate from StudentFeeLock gate where gate.id = 1")
    Optional<StudentFeeLock> lockRoster();
}
