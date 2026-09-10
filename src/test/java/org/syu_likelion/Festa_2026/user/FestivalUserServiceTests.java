package org.syu_likelion.Festa_2026.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.syu_likelion.Festa_2026.error.ApiException;
import org.syu_likelion.Festa_2026.schoolsso.SchoolSubjectHasher;

class FestivalUserServiceTests {
    private static final UUID ACTOR = UUID.fromString("123e4567-e89b-12d3-a456-426614174301");
    private static final UUID TARGET = UUID.fromString("123e4567-e89b-12d3-a456-426614174302");
    private FestivalUserRepository repository;
    private SchoolSubjectHasher schoolSubjects;
    private FestivalUserService service;

    @BeforeEach void setUp() {
        repository = mock(FestivalUserRepository.class);
        schoolSubjects = mock(SchoolSubjectHasher.class);
        service = new FestivalUserService(repository,
                Clock.fixed(Instant.parse("2026-08-18T12:00:00Z"), ZoneOffset.UTC),
                schoolSubjects, mock(org.syu_likelion.Festa_2026.fee.StudentFeeService.class));
    }

    @Test void adminCanChangeUserToStaffWithoutRemovingBoothManagerRole() {
        FestivalUser target = new FestivalUser(TARGET);
        target.addRole(FestivalRole.BOOTH_MANAGER);
        when(repository.findByUserUuid(TARGET)).thenReturn(Optional.of(target));
        when(repository.findByUserUuidForUpdate(TARGET)).thenReturn(Optional.of(target));

        assertThat(service.updateManagementRole(ACTOR, FestivalRole.ADMIN, TARGET, FestivalRole.STAFF))
                .containsExactlyInAnyOrder(FestivalRole.STAFF, FestivalRole.BOOTH_MANAGER);
        assertThat(target.getManagementRole()).isEqualTo(FestivalRole.STAFF);
        assertThat(target.isBoothManager()).isTrue();
    }

    @Test void adminCannotPromoteUserToAdminOrModifyAnotherAdmin() {
        FestivalUser user = new FestivalUser(TARGET);
        when(repository.findByUserUuid(TARGET)).thenReturn(Optional.of(user));
        when(repository.findByUserUuidForUpdate(TARGET)).thenReturn(Optional.of(user));
        assertThatThrownBy(() -> service.updateManagementRole(ACTOR, FestivalRole.ADMIN, TARGET, FestivalRole.ADMIN))
                .isInstanceOfSatisfying(ApiException.class, error ->
                        assertThat(error.code()).isEqualTo("USER_ROLE_ESCALATION_FORBIDDEN"));

        user.changeManagementRole(FestivalRole.ADMIN);
        assertThatThrownBy(() -> service.updateManagementRole(ACTOR, FestivalRole.ADMIN, TARGET, FestivalRole.STAFF))
                .isInstanceOfSatisfying(ApiException.class, error ->
                        assertThat(error.code()).isEqualTo("USER_ROLE_ESCALATION_FORBIDDEN"));
    }

    @Test void adminCannotChangeOwnRole() {
        FestivalUser actor = new FestivalUser(ACTOR); actor.changeManagementRole(FestivalRole.ADMIN);
        when(repository.findByUserUuid(ACTOR)).thenReturn(Optional.of(actor));
        when(repository.findByUserUuidForUpdate(ACTOR)).thenReturn(Optional.of(actor));
        assertThatThrownBy(() -> service.updateManagementRole(ACTOR, FestivalRole.ADMIN, ACTOR, FestivalRole.USER))
                .isInstanceOfSatisfying(ApiException.class, error ->
                        assertThat(error.code()).isEqualTo("USER_ROLE_ESCALATION_FORBIDDEN"));
    }

    @Test void superAdminCanPromoteUserButCannotDemoteLastSuperAdmin() {
        FestivalUser user = new FestivalUser(TARGET);
        when(repository.findByUserUuid(TARGET)).thenReturn(Optional.of(user));
        when(repository.findByUserUuidForUpdate(TARGET)).thenReturn(Optional.of(user));
        assertThat(service.updateManagementRole(ACTOR, FestivalRole.SUPER_ADMIN, TARGET, FestivalRole.ADMIN))
                .containsExactly(FestivalRole.ADMIN);

        user.changeManagementRole(FestivalRole.SUPER_ADMIN);
        when(repository.findAllByManagementRoleForUpdate(FestivalRole.SUPER_ADMIN)).thenReturn(List.of(user));
        assertThatThrownBy(() -> service.updateManagementRole(ACTOR, FestivalRole.SUPER_ADMIN, TARGET, FestivalRole.USER))
                .isInstanceOfSatisfying(ApiException.class, error ->
                        assertThat(error.code()).isEqualTo("LAST_SUPER_ADMIN_REQUIRED"));
    }

    @Test void boothManagerCannotBeAssignedAsManagementRole() {
        assertThatThrownBy(() -> service.updateManagementRole(ACTOR, FestivalRole.SUPER_ADMIN,
                TARGET, FestivalRole.BOOTH_MANAGER)).isInstanceOfSatisfying(ApiException.class, error ->
                        assertThat(error.code()).isEqualTo("INVALID_MANAGEMENT_ROLE"));
    }

    @Test void welcomeEmailCanBeClaimedOnlyUntilItIsCompleted() {
        FestivalUser user = new FestivalUser(TARGET);
        when(repository.findByUserUuidForUpdate(TARGET)).thenReturn(Optional.of(user));

        UUID claim = service.claimWelcomeEmail(TARGET);

        assertThat(claim).isNotNull();
        assertThat(service.claimWelcomeEmail(TARGET)).isNull();
        service.completeWelcomeEmail(TARGET, claim);
        assertThat(user.getWelcomeEmailSentAt()).isEqualTo(Instant.parse("2026-08-18T12:00:00Z"));
        assertThat(service.claimWelcomeEmail(TARGET)).isNull();
    }

    @Test void failedWelcomeEmailClaimCanBeReleasedForRetry() {
        FestivalUser user = new FestivalUser(TARGET);
        when(repository.findByUserUuidForUpdate(TARGET)).thenReturn(Optional.of(user));
        UUID first = service.claimWelcomeEmail(TARGET);

        service.releaseWelcomeEmailClaim(TARGET, first);

        assertThat(service.claimWelcomeEmail(TARGET)).isNotNull().isNotEqualTo(first);
    }

    @Test void verifiedSchoolProfileIsStoredAsFestivalOnlyState() {
        FestivalUser user = new FestivalUser(TARGET);
        Instant verifiedAt = Instant.parse("2026-08-18T11:55:00Z");
        when(repository.findByUserUuidForUpdate(TARGET)).thenReturn(Optional.of(user));
        when(schoolSubjects.hash("20260001")).thenReturn("a".repeat(64));

        FestivalUserService.UserFestivalProfile result = service.verifySchool(TARGET,
                new org.syu_likelion.Festa_2026.schoolsso.SchoolAcademicProfile(
                        "20260001", "컴퓨터공학과", "학생", "festa-2026", verifiedAt,
                        verifiedAt.plusSeconds(900)));

        assertThat(result.schoolVerified()).isTrue();
        assertThat(result.schoolVerifiedAt()).isEqualTo(verifiedAt);
        assertThat(user.getSchoolVerificationStatus()).isEqualTo(SchoolVerificationStatus.VERIFIED);
    }

    @Test void departmentEditRevokesVerificationButKeepsVerifiedAt() {
        FestivalUser user = new FestivalUser(TARGET);
        Instant verifiedAt = Instant.parse("2026-08-18T11:55:00Z");
        user.verifySchool("a".repeat(64), verifiedAt);
        when(repository.findByUserUuidForUpdate(TARGET)).thenReturn(Optional.of(user));

        service.revokeSchoolVerification(TARGET);

        assertThat(user.getSchoolVerificationStatus()).isEqualTo(SchoolVerificationStatus.REVOKED);
        assertThat(user.getSchoolVerifiedAt()).isEqualTo(verifiedAt);
    }

    @Test void adminCanVerifyAndRevokeAStudentWithoutSchoolSso() {
        FestivalUser user = new FestivalUser(TARGET);
        when(repository.findByUserUuidForUpdate(TARGET)).thenReturn(Optional.of(user));

        FestivalUserService.UserFestivalProfile verified =
                service.updateSchoolVerificationByAdmin(FestivalRole.ADMIN, TARGET, true);

        assertThat(verified.schoolVerified()).isTrue();
        assertThat(verified.schoolVerifiedAt()).isEqualTo(Instant.parse("2026-08-18T12:00:00Z"));
        assertThat(user.getSchoolVerificationStatus()).isEqualTo(SchoolVerificationStatus.VERIFIED);

        FestivalUserService.UserFestivalProfile revoked =
                service.updateSchoolVerificationByAdmin(FestivalRole.SUPER_ADMIN, TARGET, false);
        assertThat(revoked.schoolVerified()).isFalse();
        assertThat(revoked.schoolVerificationStatus()).isEqualTo(SchoolVerificationStatus.REVOKED);
    }

    @Test void staffCannotChangeStudentVerification() {
        assertThatThrownBy(() -> service.updateSchoolVerificationByAdmin(FestivalRole.STAFF, TARGET, true))
                .isInstanceOfSatisfying(ApiException.class, error ->
                        assertThat(error.code()).isEqualTo("SCHOOL_VERIFICATION_MANAGE_FORBIDDEN"));
    }
}
