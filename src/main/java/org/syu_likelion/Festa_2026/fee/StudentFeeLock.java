package org.syu_likelion.Festa_2026.fee;
import jakarta.persistence.*;
/** 명단 갱신과 학생 인증의 동시 실행을 여러 서버에서도 직렬화한다. */
@Entity @Table(name = "student_fee_lock")
public class StudentFeeLock {
    @Id private Long id;
    protected StudentFeeLock() { }
    StudentFeeLock(long id) { this.id = id; }
}
