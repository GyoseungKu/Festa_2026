package org.syu_likelion.Festa_2026.qr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.syu_likelion.Festa_2026.auth.AuthorizedSsoExecutor.AuthorizedResult;
import org.syu_likelion.Festa_2026.error.ApiException;
import org.syu_likelion.Festa_2026.sso.SsoInternalProfileClient;
import org.syu_likelion.Festa_2026.sso.SsoProfiles.InternalUserProfile;
import org.syu_likelion.Festa_2026.user.FestivalRole;
import org.syu_likelion.Festa_2026.user.FestivalUserService;
import org.syu_likelion.Festa_2026.user.SchoolVerificationStatus;
import org.syu_likelion.Festa_2026.user.UserDtos.MeResponse;
import org.syu_likelion.Festa_2026.user.UserService;

class QrServiceTests {
    private static final UUID SCANNER_UUID = UUID.fromString("123e4567-e89b-12d3-a456-426614174001");
    private static final UUID TARGET_UUID = UUID.fromString("123e4567-e89b-12d3-a456-426614174002");
    private static final Instant NOW = Instant.parse("2026-07-30T08:00:00Z");
    private UserService users;
    private FestivalUserService festivalUsers;
    private SsoInternalProfileClient profiles;
    private MemoryTokenStore tokens;
    private QrService service;

    @BeforeEach
    void setUp() {
        users = mock(UserService.class);
        festivalUsers = mock(FestivalUserService.class);
        profiles = mock(SsoInternalProfileClient.class);
        tokens = new MemoryTokenStore();
        service = new QrService(users, festivalUsers, profiles, tokens,
                new QrProperties(Duration.ofSeconds(60)), Clock.fixed(NOW, ZoneOffset.UTC));
        when(profiles.getProfile(TARGET_UUID)).thenReturn(targetProfile());
        when(festivalUsers.getRoles(TARGET_UUID)).thenReturn(Set.of(FestivalRole.USER));
        when(festivalUsers.getProfile(TARGET_UUID)).thenReturn(new FestivalUserService.UserFestivalProfile(
                Set.of(FestivalRole.USER), SchoolVerificationStatus.VERIFIED, NOW.minusSeconds(60)));
    }

    @Test
    void anyAuthenticatedUserCanIssueUnpredictableSixtySecondToken() {
        authenticateAs(FestivalRole.USER, TARGET_UUID);

        AuthorizedResult<QrDtos.QrTokenResponse> result = service.issue("access", null);

        assertThat(result.body().token()).matches("^[A-Za-z0-9_-]{43}$");
        assertThat(result.body().expiresAt()).isEqualTo(NOW.plusSeconds(60));
        assertThat(tokens.findUserUuid(result.body().token())).contains(TARGET_UUID);
        assertThat(tokens.savedTtl).isEqualTo(Duration.ofSeconds(60));
    }

    @Test
    void boothManagerSeesOnlyMaskedMinimumProfile() {
        authenticateAs(FestivalRole.BOOTH_MANAGER, SCANNER_UUID);
        tokens.save("valid-token", TARGET_UUID, Duration.ofSeconds(60));

        QrDtos.QrUserView view = service.scan("access", null, "valid-token").body();

        assertThat(view.viewerRole()).isEqualTo(FestivalRole.BOOTH_MANAGER);
        assertThat(view.name()).isEqualTo("구*승");
        assertThat(view.studentNo()).isEqualTo("2024******");
        assertThat(view.department()).isEqualTo("컴퓨터공학과");
        assertThat(view.grade()).isEqualTo(3);
        assertThat(view.phone()).isNull();
        assertThat(view.email()).isNull();
        assertThat(view.userUuid()).isNull();
        assertThat(view.schoolVerificationStatus()).isEqualTo(SchoolVerificationStatus.VERIFIED);
        assertThat(view.schoolVerified()).isTrue();
        assertThat(view.schoolVerifiedAt()).isEqualTo(NOW.minusSeconds(60));
    }

    @Test
    void staffSeesMaskedAcademicProfileButNoContactInformation() {
        authenticateAs(FestivalRole.STAFF, SCANNER_UUID);
        tokens.save("valid-token", TARGET_UUID, Duration.ofSeconds(60));

        QrDtos.QrUserView view = service.scan("access", null, "valid-token").body();

        assertThat(view.name()).isEqualTo("구*승");
        assertThat(view.studentNo()).isEqualTo("2024******");
        assertThat(view.department()).isEqualTo("컴퓨터공학과");
        assertThat(view.grade()).isEqualTo(3);
        assertThat(view.phone()).isNull();
        assertThat(view.email()).isNull();
    }

    @Test
    void adminAlsoSeesPhoneAndEmail() {
        authenticateAs(FestivalRole.ADMIN, SCANNER_UUID);
        tokens.save("valid-token", TARGET_UUID, Duration.ofSeconds(60));

        QrDtos.QrUserView view = service.scan("access", null, "valid-token").body();

        assertThat(view.phone()).isEqualTo("01012345678");
        assertThat(view.email()).isEqualTo("target@example.com");
        assertThat(view.userUuid()).isEqualTo(TARGET_UUID);
        assertThat(view.festivalRoles()).containsExactly(FestivalRole.USER);
        assertThat(view.birthDate()).isNull();
        assertThat(view.loginId()).isNull();
    }

    @Test
    void superAdminSeesFullSsoProfileAndFestivalRoles() {
        authenticateAs(FestivalRole.SUPER_ADMIN, SCANNER_UUID);
        tokens.save("valid-token", TARGET_UUID, Duration.ofSeconds(60));

        QrDtos.QrUserView view = service.scan("access", null, "valid-token").body();

        assertThat(view.userUuid()).isEqualTo(TARGET_UUID);
        assertThat(view.loginId()).isEqualTo("target01");
        assertThat(view.birthDate()).isEqualTo("2004-01-02");
        assertThat(view.enrollment()).isEqualTo("ENROLLED");
        assertThat(view.festivalRoles()).containsExactly(FestivalRole.USER);
    }

    @Test
    void staffCanSearchWithOriginalNameButReceivesOnlyMaskedResult() {
        when(festivalUsers.getLinkedUserUuids()).thenReturn(List.of(TARGET_UUID));
        when(profiles.getProfiles(List.of(TARGET_UUID))).thenReturn(List.of(targetProfile()));

        QrDtos.UserSearchResponse result = service.searchAs(FestivalRole.STAFF, "구요승");

        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.items()).singleElement().satisfies(view -> {
            assertThat(view.name()).isEqualTo("구*승");
            assertThat(view.studentNo()).isEqualTo("2024******");
            assertThat(view.phone()).isNull();
            assertThat(view.email()).isNull();
            assertThat(view.schoolVerificationStatus()).isEqualTo(SchoolVerificationStatus.VERIFIED);
            assertThat(view.schoolVerified()).isTrue();
            assertThat(view.schoolVerifiedAt()).isEqualTo(NOW.minusSeconds(60));
        });
    }

    @Test
    void adminCanSearchByPhoneAndReceivesAdminScope() {
        when(festivalUsers.getLinkedUserUuids()).thenReturn(List.of(TARGET_UUID));
        when(profiles.getProfiles(List.of(TARGET_UUID))).thenReturn(List.of(targetProfile()));

        QrDtos.UserSearchResponse result = service.searchAs(FestivalRole.ADMIN, "010-1234-5678");

        assertThat(result.items()).singleElement().satisfies(view -> {
            assertThat(view.name()).isEqualTo("구요승");
            assertThat(view.phone()).isEqualTo("01012345678");
            assertThat(view.userUuid()).isEqualTo(TARGET_UUID);
        });
    }

    @Test
    void authenticatedAdminCanUpdateManagementRole() {
        authenticateAs(FestivalRole.ADMIN, SCANNER_UUID);
        when(festivalUsers.updateManagementRole(SCANNER_UUID, FestivalRole.ADMIN, TARGET_UUID, FestivalRole.STAFF))
                .thenReturn(Set.of(FestivalRole.STAFF));

        QrDtos.UserRoleUpdateResponse result = service.updateRole("access", null, TARGET_UUID,
                FestivalRole.STAFF).body();

        assertThat(result.userUuid()).isEqualTo(TARGET_UUID);
        assertThat(result.festivalRoles()).containsExactly(FestivalRole.STAFF);
    }

    @Test
    void boothManagerCannotUseDirectorySearch() {
        assertThatThrownBy(() -> service.searchAs(FestivalRole.BOOTH_MANAGER, "구요승"))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.code()).isEqualTo("USER_SEARCH_FORBIDDEN"));
        verify(festivalUsers, never()).getLinkedUserUuids();
    }

    @Test
    void normalizedSearchQueryMustContainAtLeastTwoCharacters() {
        assertThatThrownBy(() -> service.searchAs(FestivalRole.STAFF, "가 "))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.code()).isEqualTo("INVALID_USER_SEARCH_QUERY"));
        verify(festivalUsers, never()).getLinkedUserUuids();
    }

    @Test
    void departmentSearchReturnsTwentyUsersPerPage() {
        List<InternalUserProfile> all = java.util.stream.IntStream.range(0, 45).mapToObj(index -> {
            UUID id = UUID.nameUUIDFromBytes(("profile-" + index).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return new InternalUserProfile(id, "user" + index, "user" + index + "@example.com", "USER", "ACTIVE",
                    "사용자" + index, null, "2024" + String.format("%06d", index), "컴퓨터공학과", 3,
                    "ENROLLED", null, null, null);
        }).toList();
        List<UUID> ids = all.stream().map(InternalUserProfile::userUuid).toList();
        when(festivalUsers.getLinkedUserUuids()).thenReturn(ids);
        when(profiles.getProfiles(ids)).thenReturn(all);

        QrDtos.UserSearchResponse second = service.searchAs(FestivalRole.STAFF, "컴퓨터공학과", 1, 20);
        QrDtos.UserSearchResponse third = service.searchAs(FestivalRole.STAFF, "컴퓨터공학과", 2, 20);

        assertThat(second.items()).hasSize(20);
        assertThat(second.page()).isEqualTo(1);
        assertThat(second.totalElements()).isEqualTo(45);
        assertThat(second.totalPages()).isEqualTo(3);
        assertThat(third.items()).hasSize(5);
    }

    @Test
    void outOfRangeSearchPageIsClampedToLastPage() {
        List<InternalUserProfile> all = java.util.stream.IntStream.range(0, 21).mapToObj(index -> {
            UUID id = UUID.nameUUIDFromBytes(("department-" + index).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return new InternalUserProfile(id, "student" + index, null, "USER", "ACTIVE",
                    "학생" + index, null, "2024" + String.format("%06d", index), "간호학과", 2,
                    "ENROLLED", null, null, null);
        }).toList();
        List<UUID> ids = all.stream().map(InternalUserProfile::userUuid).toList();
        when(festivalUsers.getLinkedUserUuids()).thenReturn(ids);
        when(profiles.getProfiles(ids)).thenReturn(all);

        QrDtos.UserSearchResponse result = service.searchAs(FestivalRole.ADMIN, "간호학과", Integer.MAX_VALUE, 20);

        assertThat(result.page()).isEqualTo(1);
        assertThat(result.items()).hasSize(1);
    }

    @Test
    void normalUserCannotScanAndTokenIsNotLookedUp() {
        authenticateAs(FestivalRole.USER, SCANNER_UUID);

        assertThatThrownBy(() -> service.scan("access", null, "anything"))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.code()).isEqualTo("QR_SCAN_FORBIDDEN"));
        verify(profiles, never()).getProfile(any());
        assertThat(tokens.lookups).isZero();
    }

    @Test
    void missingOrExpiredTokenIsRejectedBeforeSsoProfileLookup() {
        authenticateAs(FestivalRole.ADMIN, SCANNER_UUID);

        assertThatThrownBy(() -> service.scan("access", null, "expired"))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.code()).isEqualTo("QR_INVALID_OR_EXPIRED"));
        verify(profiles, never()).getProfile(any());
    }

    private void authenticateAs(FestivalRole role, UUID uuid) {
        when(users.getMe(any(), any())).thenReturn(new AuthorizedResult<>(scanner(role, uuid), null, null));
    }

    private MeResponse scanner(FestivalRole role, UUID uuid) {
        return new MeResponse(uuid, "scanner", "scanner@example.com", "USER", "ACTIVE",
                "관리자", null, null, null, null, null, null,
                LocalDateTime.of(2026, 1, 1, 0, 0), LocalDateTime.of(2026, 1, 1, 0, 0), Set.of(role));
    }

    private InternalUserProfile targetProfile() {
        return new InternalUserProfile(TARGET_UUID, "target01", "target@example.com", "USER", "ACTIVE",
                "구요승", "01012345678", "2024100920", "컴퓨터공학과", 3, "ENROLLED",
                "2004-01-02", "2026-01-01T00:00:00", "2026-07-30T00:00:00");
    }

    private static final class MemoryTokenStore implements QrTokenStore {
        private final Map<String, UUID> values = new HashMap<>();
        private Duration savedTtl;
        private int lookups;

        @Override
        public void save(String token, UUID userUuid, Duration ttl) {
            values.put(token, userUuid);
            savedTtl = ttl;
        }

        @Override
        public Optional<UUID> findUserUuid(String token) {
            lookups++;
            return Optional.ofNullable(values.get(token));
        }
    }
}
