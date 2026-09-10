package org.syu_likelion.Festa_2026.fee;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "student_fee_payers")
public class StudentFeePayer {
    @Id @Column(length = 10) private String studentNo;
    @Column(nullable = false, unique = true, length = 64) private String subjectHash;
    @Column(nullable = false) private Instant createdAt;
    @Column(nullable = false, length = 36) private String createdBy;
    protected StudentFeePayer() { }
    StudentFeePayer(String number, String hash, String actor, Instant now) {
        studentNo = number; subjectHash = hash; createdBy = actor; createdAt = now;
    }
    public String getStudentNo() { return studentNo; }
    public String getSubjectHash() { return subjectHash; }
    public Instant getCreatedAt() { return createdAt; }
}
