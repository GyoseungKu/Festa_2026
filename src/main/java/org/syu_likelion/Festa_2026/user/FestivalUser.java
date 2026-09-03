package org.syu_likelion.Festa_2026.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "festival_users")
public class FestivalUser {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "user_uuid", nullable = false, unique = true, updatable = false,
            length = 36, columnDefinition = "CHAR(36)")
    private UUID userUuid;

    @Column(name = "management_role", nullable = false,
            columnDefinition = "enum('SUPER_ADMIN','ADMIN','STAFF','USER') default 'USER'")
    @Enumerated(EnumType.STRING)
    private FestivalRole managementRole = FestivalRole.USER;

    @Column(name = "booth_manager", nullable = false, columnDefinition = "boolean default false")
    private boolean boothManager;

    @Column(name = "school_verification_status", nullable = false,
            columnDefinition = "enum('UNVERIFIED','VERIFIED','REVOKED') default 'UNVERIFIED'")
    @Enumerated(EnumType.STRING)
    private SchoolVerificationStatus schoolVerificationStatus = SchoolVerificationStatus.UNVERIFIED;

    @Column(name = "school_verified_at")
    private Instant schoolVerifiedAt;

    @Column(name = "school_subject_hash", length = 64, unique = true)
    private String schoolSubjectHash;

    @Column(name = "welcome_email_sent_at")
    private Instant welcomeEmailSentAt;

    @Column(name = "welcome_email_pending", nullable = false, columnDefinition = "boolean default false")
    private boolean welcomeEmailPending;

    @Column(name = "welcome_email_claim_token", length = 36)
    private String welcomeEmailClaimToken;

    @Column(name = "welcome_email_claimed_at")
    private Instant welcomeEmailClaimedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected FestivalUser() { }

    public FestivalUser(UUID userUuid) {
        this.userUuid = userUuid;
        this.welcomeEmailPending = true;
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() { updatedAt = Instant.now(); }

    public UUID getUserUuid() { return userUuid; }
    public Set<FestivalRole> getRoles() {
        EnumSet<FestivalRole> roles = EnumSet.of(managementRole);
        if (boothManager) roles.add(FestivalRole.BOOTH_MANAGER);
        return Set.copyOf(roles);
    }
    public Long getId() { return id; }
    public FestivalRole getManagementRole() { return managementRole; }
    public boolean isBoothManager() { return boothManager; }
    public SchoolVerificationStatus getSchoolVerificationStatus() { return schoolVerificationStatus; }
    public Instant getSchoolVerifiedAt() { return schoolVerifiedAt; }
    public Instant getWelcomeEmailSentAt() { return welcomeEmailSentAt; }
    public boolean isWelcomeEmailPending() { return welcomeEmailPending; }
    public String getWelcomeEmailClaimToken() { return welcomeEmailClaimToken; }
    public Instant getWelcomeEmailClaimedAt() { return welcomeEmailClaimedAt; }
    public void changeManagementRole(FestivalRole role) { managementRole = role; }

    public void verifySchool(String subjectHash, Instant verifiedAt) {
        schoolVerificationStatus = SchoolVerificationStatus.VERIFIED;
        schoolSubjectHash = subjectHash;
        schoolVerifiedAt = verifiedAt;
    }

    /** 관리자 확인은 학교 SSO 식별자를 새로 만들거나 기존 식별자를 덮어쓰지 않습니다. */
    public void verifySchoolByAdmin(Instant verifiedAt) {
        if (verifiedAt == null) throw new IllegalArgumentException("Verification time is required");
        schoolVerificationStatus = SchoolVerificationStatus.VERIFIED;
        schoolVerifiedAt = verifiedAt;
    }

    public void revokeSchoolVerification() {
        if (schoolVerificationStatus == SchoolVerificationStatus.VERIFIED) {
            schoolVerificationStatus = SchoolVerificationStatus.REVOKED;
        }
    }

    public void claimWelcomeEmail(UUID claimToken, Instant claimedAt) {
        welcomeEmailClaimToken = claimToken.toString();
        welcomeEmailClaimedAt = claimedAt;
    }

    public boolean ownsWelcomeEmailClaim(UUID claimToken) {
        return claimToken != null && claimToken.toString().equals(welcomeEmailClaimToken);
    }

    public void completeWelcomeEmail(UUID claimToken, Instant sentAt) {
        if (!ownsWelcomeEmailClaim(claimToken)) return;
        welcomeEmailSentAt = sentAt;
        welcomeEmailPending = false;
        clearWelcomeEmailClaim();
    }

    public void releaseWelcomeEmailClaim(UUID claimToken) {
        if (ownsWelcomeEmailClaim(claimToken)) clearWelcomeEmailClaim();
    }

    private void clearWelcomeEmailClaim() {
        welcomeEmailClaimToken = null;
        welcomeEmailClaimedAt = null;
    }

    public void addRole(FestivalRole role) {
        if (role == FestivalRole.BOOTH_MANAGER) boothManager = true;
        else managementRole = role;
    }

    public void removeRole(FestivalRole role) {
        if (role == FestivalRole.BOOTH_MANAGER) boothManager = false;
        else if (managementRole == role) managementRole = FestivalRole.USER;
    }
}
