package org.syu_likelion.Festa_2026.user;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.syu_likelion.Festa_2026.error.ApiException;
import org.syu_likelion.Festa_2026.schoolsso.SchoolAcademicProfile;
import org.syu_likelion.Festa_2026.schoolsso.SchoolSubjectHasher;

@Service
public class FestivalUserService {
    private static final Duration WELCOME_EMAIL_CLAIM_TIMEOUT = Duration.ofMinutes(15);
    private final FestivalUserRepository repository;
    private final Clock clock;
    private final SchoolSubjectHasher schoolSubjects;

    public FestivalUserService(FestivalUserRepository repository, Clock clock, SchoolSubjectHasher schoolSubjects) {
        this.repository = repository;
        this.clock = clock;
        this.schoolSubjects = schoolSubjects;
    }

    @Transactional
    public Set<FestivalRole> linkAndGetRoles(UUID userUuid) {
        if (userUuid == null) throw new IllegalArgumentException("SSO response did not contain userUuid");
        return repository.findByUserUuid(userUuid).orElseGet(() -> create(userUuid)).getRoles();
    }

    @Transactional(readOnly = true)
    public Set<FestivalRole> getRoles(UUID userUuid) {
        return repository.findByUserUuid(userUuid).map(FestivalUser::getRoles).orElseGet(Set::of);
    }

    @Transactional
    public UserFestivalProfile linkAndGetProfile(UUID userUuid) {
        if (userUuid == null) throw new IllegalArgumentException("SSO response did not contain userUuid");
        return toProfile(repository.findByUserUuid(userUuid).orElseGet(() -> create(userUuid)));
    }

    @Transactional(readOnly = true)
    public UserFestivalProfile getProfile(UUID userUuid) {
        return repository.findByUserUuid(userUuid).map(this::toProfile)
                .orElse(new UserFestivalProfile(Set.of(), SchoolVerificationStatus.UNVERIFIED, null));
    }

    @Transactional
    public UserFestivalProfile verifySchool(UUID userUuid, SchoolAcademicProfile schoolProfile) {
        if (schoolProfile == null) throw new IllegalArgumentException("School profile is required");
        FestivalUser user = repository.findByUserUuidForUpdate(userUuid).orElseGet(() -> create(userUuid));
        try {
            user.verifySchool(schoolSubjects.hash(schoolProfile.studentNo()), schoolProfile.verifiedAt());
            repository.saveAndFlush(user);
            return toProfile(user);
        } catch (DataIntegrityViolationException duplicateSchoolSubject) {
            throw new ApiException(HttpStatus.CONFLICT, "SCHOOL_IDENTITY_ALREADY_LINKED",
                    "이미 다른 축제 계정에 연결된 학교 인증정보입니다.");
        }
    }

    @Transactional
    public void revokeSchoolVerification(UUID userUuid) {
        repository.findByUserUuidForUpdate(userUuid).ifPresent(FestivalUser::revokeSchoolVerification);
    }

    @Transactional
    public UserFestivalProfile updateSchoolVerificationByAdmin(FestivalRole actorRole, UUID targetUuid,
                                                                 boolean verified) {
        if (actorRole != FestivalRole.ADMIN && actorRole != FestivalRole.SUPER_ADMIN)
            throw new ApiException(HttpStatus.FORBIDDEN, "SCHOOL_VERIFICATION_MANAGE_FORBIDDEN",
                    "학생 인증은 ADMIN 이상만 변경할 수 있습니다.");
        FestivalUser target = repository.findByUserUuidForUpdate(targetUuid).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "FESTIVAL_USER_NOT_FOUND",
                        "축제 연동 사용자를 찾을 수 없습니다."));
        if (verified) target.verifySchoolByAdmin(clock.instant());
        else target.revokeSchoolVerification();
        return toProfile(target);
    }

    @Transactional(readOnly = true)
    public List<UUID> getLinkedUserUuids() { return repository.findAllUserUuids(); }

    @Transactional
    public UUID claimWelcomeEmail(UUID userUuid) {
        FestivalUser user = repository.findByUserUuidForUpdate(userUuid).orElse(null);
        if (user == null || !user.isWelcomeEmailPending() || user.getWelcomeEmailSentAt() != null) return null;
        Instant now = clock.instant();
        Instant claimedAt = user.getWelcomeEmailClaimedAt();
        if (claimedAt != null && now.isBefore(claimedAt.plus(WELCOME_EMAIL_CLAIM_TIMEOUT))) return null;
        UUID claimToken = UUID.randomUUID();
        user.claimWelcomeEmail(claimToken, now);
        return claimToken;
    }

    @Transactional
    public void completeWelcomeEmail(UUID userUuid, UUID claimToken) {
        repository.findByUserUuidForUpdate(userUuid)
                .ifPresent(user -> user.completeWelcomeEmail(claimToken, clock.instant()));
    }

    @Transactional
    public void releaseWelcomeEmailClaim(UUID userUuid, UUID claimToken) {
        repository.findByUserUuidForUpdate(userUuid)
                .ifPresent(user -> user.releaseWelcomeEmailClaim(claimToken));
    }

    @Transactional
    public Set<FestivalRole> updateManagementRole(UUID actorUuid, FestivalRole actorRole,
                                                   UUID targetUuid, FestivalRole requestedRole) {
        if (actorRole != FestivalRole.ADMIN && actorRole != FestivalRole.SUPER_ADMIN)
            throw new ApiException(HttpStatus.FORBIDDEN, "USER_ROLE_MANAGE_FORBIDDEN",
                    "사용자 권한은 ADMIN 이상만 변경할 수 있습니다.");
        if (requestedRole == null || requestedRole == FestivalRole.BOOTH_MANAGER)
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_MANAGEMENT_ROLE",
                    "관리 권한은 USER, STAFF, ADMIN, SUPER_ADMIN 중에서 선택해 주세요.");
        FestivalUser preview = repository.findByUserUuid(targetUuid).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "FESTIVAL_USER_NOT_FOUND", "축제 연동 사용자를 찾을 수 없습니다."));
        List<FestivalUser> lockedSuperAdmins = preview.getManagementRole() == FestivalRole.SUPER_ADMIN
                && requestedRole != FestivalRole.SUPER_ADMIN
                ? repository.findAllByManagementRoleForUpdate(FestivalRole.SUPER_ADMIN) : List.of();
        FestivalUser target = lockedSuperAdmins.stream().filter(user -> targetUuid.equals(user.getUserUuid()))
                .findFirst().orElseGet(() -> repository.findByUserUuidForUpdate(targetUuid).orElseThrow(() ->
                        new ApiException(HttpStatus.NOT_FOUND, "FESTIVAL_USER_NOT_FOUND", "축제 연동 사용자를 찾을 수 없습니다.")));
        FestivalRole current = target.getManagementRole();
        if (actorRole == FestivalRole.ADMIN) {
            boolean protectedTarget = actorUuid.equals(targetUuid)
                    || current == FestivalRole.ADMIN || current == FestivalRole.SUPER_ADMIN;
            boolean privilegedRequest = requestedRole == FestivalRole.ADMIN || requestedRole == FestivalRole.SUPER_ADMIN;
            if (protectedTarget || privilegedRequest)
                throw new ApiException(HttpStatus.FORBIDDEN, "USER_ROLE_ESCALATION_FORBIDDEN",
                        "ADMIN은 다른 USER 또는 STAFF의 권한만 USER·STAFF 범위에서 변경할 수 있습니다.");
        }
        if (current == FestivalRole.SUPER_ADMIN && requestedRole != FestivalRole.SUPER_ADMIN
                && lockedSuperAdmins.size() <= 1)
                throw new ApiException(HttpStatus.CONFLICT, "LAST_SUPER_ADMIN_REQUIRED",
                        "마지막 SUPER_ADMIN의 권한은 낮출 수 없습니다.");
        target.changeManagementRole(requestedRole);
        return target.getRoles();
    }

    private FestivalUser create(UUID userUuid) {
        try {
            return repository.saveAndFlush(new FestivalUser(userUuid));
        } catch (DataIntegrityViolationException concurrentInsert) {
            return repository.findByUserUuid(userUuid).orElseThrow(() -> concurrentInsert);
        }
    }

    private UserFestivalProfile toProfile(FestivalUser user) {
        return new UserFestivalProfile(user.getRoles(), user.getSchoolVerificationStatus(),
                user.getSchoolVerifiedAt());
    }

    public record UserFestivalProfile(Set<FestivalRole> roles,
                                      SchoolVerificationStatus schoolVerificationStatus,
                                      Instant schoolVerifiedAt) {
        public boolean schoolVerified() {
            return schoolVerificationStatus == SchoolVerificationStatus.VERIFIED && schoolVerifiedAt != null;
        }
    }
}
