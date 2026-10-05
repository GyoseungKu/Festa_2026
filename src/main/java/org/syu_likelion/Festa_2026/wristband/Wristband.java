package org.syu_likelion.Festa_2026.wristband;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "festival_wristbands", indexes = @Index(name = "idx_wristband_updated", columnList = "updated_at,id"))
public class Wristband {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Version private long version;
    @Column(nullable = false, unique = true, updatable = false, length = 64)
    private String subjectHash;
    @Column(nullable = false) private boolean issued;
    @JdbcTypeCode(SqlTypes.CHAR) @Column(length = 36, columnDefinition = "CHAR(36)", unique = true)
    private UUID activeUserUuid;
    @JdbcTypeCode(SqlTypes.CHAR) @Column(length = 36, columnDefinition = "CHAR(36)")
    private UUID targetUserUuid;
    @Column(nullable = false, length = 200) private String targetName;
    @Column(length = 10) private String targetStudentNo;
    @Column(length = 100) private String targetDepartment;
    @JdbcTypeCode(SqlTypes.CHAR) @Column(nullable = false, length = 36, columnDefinition = "CHAR(36)")
    private UUID issuedBy;
    @Column(nullable = false, length = 200) private String issuerName;
    @Column(nullable = false) private Instant issuedAt;
    @Column(nullable = false) private Instant updatedAt;

    protected Wristband() { }
    public Wristband(String subjectHash) { this.subjectHash = subjectHash; }
    public void issue(UUID target, String name, UUID actor, String actorName, Instant now) {
        issued = true; activeUserUuid = target; targetUserUuid = target; targetName = name;
        targetStudentNo = null; targetDepartment = null;
        issuedBy = actor; issuerName = actorName; issuedAt = now; updatedAt = now;
    }
    public void issueWithoutAccount(String studentNo, String name, String department,
            UUID actor, String actorName, Instant now) {
        issue(null, name, actor, actorName, now);
        targetStudentNo = studentNo; targetDepartment = department;
    }
    public void updateManualProfile(String name, String department, Instant now) {
        targetName = name; targetDepartment = department; updatedAt = now;
    }
    public void revoke(Instant now) { issued = false; activeUserUuid = null; updatedAt = now; }
    public Long getId() { return id; }
    public long getVersion() { return version; }
    public boolean isIssued() { return issued; }
    public UUID getActiveUserUuid() { return activeUserUuid; }
    public String getSubjectHash() { return subjectHash; }
    public UUID getTargetUserUuid() { return targetUserUuid; }
    public String getTargetName() { return targetName; }
    public String getTargetStudentNo() { return targetStudentNo; }
    public String getTargetDepartment() { return targetDepartment; }
    public UUID getIssuedBy() { return issuedBy; }
    public String getIssuerName() { return issuerName; }
    public Instant getIssuedAt() { return issuedAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
