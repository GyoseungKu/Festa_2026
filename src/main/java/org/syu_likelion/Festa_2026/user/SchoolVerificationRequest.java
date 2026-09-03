package org.syu_likelion.Festa_2026.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "school_verification_requests")
public class SchoolVerificationRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_uuid", nullable = false, unique = true, columnDefinition = "BINARY(16)")
    private UUID userUuid;
    @Column(name = "current_name", nullable = false, length = 100)
    private String currentName;
    @Column(name = "current_student_no", nullable = false, length = 50)
    private String currentStudentNo;
    @Column(name = "current_department", length = 100)
    private String currentDepartment;
    @Column(name = "school_name", nullable = false, length = 100)
    private String schoolName;
    @Column(name = "school_student_no", nullable = false, length = 50)
    private String schoolStudentNo;
    @Column(name = "school_department", nullable = false, length = 100)
    private String schoolDepartment;
    @Column(name = "school_subject_hash", nullable = false, length = 64)
    private String schoolSubjectHash;
    @Column(name = "school_verified_at", nullable = false)
    private Instant schoolVerifiedAt;
    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    protected SchoolVerificationRequest() { }

    public SchoolVerificationRequest(UUID userUuid, String currentName, String currentStudentNo,
                                     String currentDepartment, String schoolName, String schoolStudentNo,
                                     String schoolDepartment, String schoolSubjectHash, Instant schoolVerifiedAt) {
        this.userUuid = userUuid;
        this.currentName = currentName;
        this.currentStudentNo = currentStudentNo;
        this.currentDepartment = currentDepartment;
        this.schoolName = schoolName;
        this.schoolStudentNo = schoolStudentNo;
        this.schoolDepartment = schoolDepartment;
        this.schoolSubjectHash = schoolSubjectHash;
        this.schoolVerifiedAt = schoolVerifiedAt;
    }

    @PrePersist
    void prePersist() { requestedAt = Instant.now(); }

    public Long getId() { return id; }
    public UUID getUserUuid() { return userUuid; }
    public String getCurrentName() { return currentName; }
    public String getCurrentStudentNo() { return currentStudentNo; }
    public String getCurrentDepartment() { return currentDepartment; }
    public String getSchoolName() { return schoolName; }
    public String getSchoolStudentNo() { return schoolStudentNo; }
    public String getSchoolDepartment() { return schoolDepartment; }
    public String getSchoolSubjectHash() { return schoolSubjectHash; }
    public Instant getSchoolVerifiedAt() { return schoolVerifiedAt; }
    public Instant getRequestedAt() { return requestedAt; }

    public void replace(String currentName, String currentStudentNo, String currentDepartment,
                        String schoolName, String schoolStudentNo, String schoolDepartment,
                        String schoolSubjectHash, Instant schoolVerifiedAt) {
        this.currentName = currentName;
        this.currentStudentNo = currentStudentNo;
        this.currentDepartment = currentDepartment;
        this.schoolName = schoolName;
        this.schoolStudentNo = schoolStudentNo;
        this.schoolDepartment = schoolDepartment;
        this.schoolSubjectHash = schoolSubjectHash;
        this.schoolVerifiedAt = schoolVerifiedAt;
        this.requestedAt = Instant.now();
    }
}
