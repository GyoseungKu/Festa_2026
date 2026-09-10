package org.syu_likelion.Festa_2026.fee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
public interface StudentFeePayerRepository extends JpaRepository<StudentFeePayer, String> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select p from StudentFeePayer p order by p.studentNo")
    java.util.List<StudentFeePayer> findAllForUpdate();
    Page<StudentFeePayer> findByStudentNoContaining(String query, Pageable pageable);
}
