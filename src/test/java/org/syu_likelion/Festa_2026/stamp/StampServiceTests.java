package org.syu_likelion.Festa_2026.stamp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.syu_likelion.Festa_2026.auth.AuthorizedSsoExecutor.AuthorizedResult;
import org.syu_likelion.Festa_2026.booth.FestivalBooth;
import org.syu_likelion.Festa_2026.booth.FestivalBoothRepository;
import org.syu_likelion.Festa_2026.error.ApiException;
import org.syu_likelion.Festa_2026.qr.QrService;
import org.syu_likelion.Festa_2026.sso.SsoInternalProfileClient;
import org.syu_likelion.Festa_2026.sso.SsoProfiles.InternalUserProfile;
import org.syu_likelion.Festa_2026.user.FestivalRole;
import org.syu_likelion.Festa_2026.user.FestivalUser;
import org.syu_likelion.Festa_2026.user.FestivalUserRepository;
import org.syu_likelion.Festa_2026.user.UserDtos.MeResponse;
import org.syu_likelion.Festa_2026.user.UserService;

class StampServiceTests {
    private static final UUID ACTOR = UUID.fromString("123e4567-e89b-12d3-a456-426614174021");
    private static final UUID TARGET = UUID.fromString("123e4567-e89b-12d3-a456-426614174022");
    private static final Instant NOW = Instant.parse("2026-08-17T03:00:00Z");
    private BoothStampRepository stamps;
    private StampEventRepository events;
    private FestivalBoothRepository booths;
    private FestivalUserRepository festivalUsers;
    private UserService users;
    private QrService qr;
    private SsoInternalProfileClient profiles;
    private StampService service;
    private FestivalBooth booth;

    @BeforeEach void setUp() {
        stamps = mock(BoothStampRepository.class); events = mock(StampEventRepository.class);
        booths = mock(FestivalBoothRepository.class); festivalUsers = mock(FestivalUserRepository.class);
        users = mock(UserService.class); qr = mock(QrService.class); profiles = mock(SsoInternalProfileClient.class);
        service = new StampService(stamps, events, booths, festivalUsers, users, qr, profiles,
                Clock.fixed(NOW, ZoneOffset.UTC));
        booth = mock(FestivalBooth.class);
        when(booth.getId()).thenReturn(1L); when(booth.getName()).thenReturn("체험 부스"); when(booth.getOperator()).thenReturn("운영팀");
        when(booth.isStampEnabled()).thenReturn(true);
        when(booths.findById(1L)).thenReturn(Optional.of(booth));
        when(festivalUsers.findByUserUuidForUpdate(TARGET)).thenReturn(Optional.of(new FestivalUser(TARGET)));
        when(qr.resolveUserUuid("qr-token")).thenReturn(TARGET);
        when(profiles.getProfile(TARGET)).thenReturn(profile(TARGET, "홍길동", "2026000001"));
    }

    @Test void boothManagerOnlySeesAssignedBooths() {
        when(booths.findAllByStampEnabledTrueAndManagersUserUuidOrderByNameAsc(ACTOR)).thenReturn(List.of(booth));
        assertThat(service.availableBoothsAs(ACTOR, FestivalRole.BOOTH_MANAGER)).containsExactly(booth);
    }

    @Test void stampDisabledBoothRejectsStampActions() {
        when(booth.isStampEnabled()).thenReturn(false);
        assertThatThrownBy(() -> service.lookupQrAs(ACTOR, FestivalRole.ADMIN, 1L, "qr-token"))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("STAMP_DISABLED_BOOTH"));
    }

    @Test void staffHasNoStampPermission() {
        assertThatThrownBy(() -> service.availableBoothsAs(ACTOR, FestivalRole.STAFF))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("STAMP_MANAGE_FORBIDDEN"));
    }

    @Test void boothManagerCannotLookupQrForAnotherBooth() {
        when(booths.existsByIdAndManagersUserUuid(1L, ACTOR)).thenReturn(false);
        assertThatThrownBy(() -> service.lookupQrAs(ACTOR, FestivalRole.BOOTH_MANAGER, 1L, "qr-token"))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.status().value()).isEqualTo(403));
    }

    @Test void qrLookupMasksUserAndReturnsCurrentState() {
        when(booths.existsByIdAndManagersUserUuid(1L, ACTOR)).thenReturn(true);
        var result = service.lookupQrAs(ACTOR, FestivalRole.BOOTH_MANAGER, 1L, "qr-token");
        assertThat(result.userUuid()).isNull();
        assertThat(result.name()).isEqualTo("홍*동");
        assertThat(result.studentNo()).isEqualTo("2026******");
        assertThat(result.stamped()).isFalse();
    }

    @Test void qrGrantCreatesCurrentStampAndAuditEvent() {
        FestivalUser targetUser = mock(FestivalUser.class);
        BoothStamp saved = mock(BoothStamp.class);
        when(saved.getGrantedAt()).thenReturn(NOW);
        when(booths.existsByIdAndManagersUserUuid(1L, ACTOR)).thenReturn(true);
        when(festivalUsers.findByUserUuidForUpdate(TARGET)).thenReturn(Optional.of(targetUser));
        when(stamps.findByBoothIdAndUserUserUuid(1L, TARGET)).thenReturn(Optional.of(saved));
        var result = service.grantQrAs(ACTOR, FestivalRole.BOOTH_MANAGER, 1L, "qr-token");
        assertThat(result.stamped()).isTrue();
        verify(stamps).saveAndFlush(any(BoothStamp.class));
        verify(events).save(any(StampEvent.class));
    }

    @Test void duplicateStampIsRejectedPerBooth() {
        when(booths.existsByIdAndManagersUserUuid(1L, ACTOR)).thenReturn(true);
        when(stamps.existsByBoothIdAndUserUserUuid(1L, TARGET)).thenReturn(true);
        assertThatThrownBy(() -> service.grantQrAs(ACTOR, FestivalRole.BOOTH_MANAGER, 1L, "qr-token"))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("STAMP_ALREADY_GRANTED"));
    }

    @Test void qrRevokeRemovesCurrentStampAndKeepsAuditEvent() {
        BoothStamp existing = mock(BoothStamp.class);
        when(booths.existsByIdAndManagersUserUuid(1L, ACTOR)).thenReturn(true);
        when(stamps.findByBoothIdAndUserUserUuid(1L, TARGET))
                .thenReturn(Optional.of(existing)).thenReturn(Optional.empty());
        var result = service.revokeQrAs(ACTOR, FestivalRole.BOOTH_MANAGER, 1L, "qr-token");
        assertThat(result.stamped()).isFalse();
        verify(stamps).delete(existing);
        verify(events).save(any(StampEvent.class));
    }

    @Test void stampBoardRemainsParticipatedAfterAllStampsAreRevoked() {
        authenticate(FestivalRole.USER);
        when(stamps.findAllByUserUserUuidOrderByGrantedAtAsc(TARGET)).thenReturn(List.of());
        when(events.existsByTargetUserUuidAndAction(TARGET, StampAction.GRANT)).thenReturn(true);
        var result = service.myBoard("access", "refresh");
        assertThat(result.body().participated()).isTrue();
        assertThat(result.body().stampCount()).isZero();
    }

    @Test void boothManagerCannotUseAdminSearchGrant() {
        authenticate(FestivalRole.BOOTH_MANAGER);
        assertThatThrownBy(() -> service.grantBySearch(1L, TARGET, "access", "refresh"))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("STAMP_MANAGE_FORBIDDEN"));
    }

    @Test void boothManagerSeesMaskedHistoryForAssignedBooth() {
        FestivalUser targetUser = new FestivalUser(TARGET);
        BoothStamp current = new BoothStamp(booth, targetUser, NOW, ACTOR, StampMethod.QR);
        StampEvent event = new StampEvent(booth, TARGET, ACTOR, StampAction.GRANT, StampMethod.QR, NOW);
        when(booths.existsByIdAndManagersUserUuid(1L, ACTOR)).thenReturn(true);
        when(stamps.findAllByBoothIdOrderByGrantedAtDesc(1L)).thenReturn(List.of(current));
        when(events.findAllByBoothId(org.mockito.ArgumentMatchers.eq(1L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(event)));
        when(profiles.getProfiles(any())).thenReturn(List.of(
                profile(TARGET, "홍길동", "2026000001"), profile(ACTOR, "김관리", "2026000002")));

        var result = service.historyAs(ACTOR, FestivalRole.BOOTH_MANAGER, 1L);

        assertThat(result.currentStamps().getFirst().userUuid()).isNull();
        assertThat(result.currentStamps().getFirst().name()).isEqualTo("홍*동");
        assertThat(result.currentStamps().getFirst().studentNo()).isEqualTo("2026******");
        assertThat(result.currentStamps().getFirst().grantedBy()).isNull();
        assertThat(result.history().getFirst().targetUserUuid()).isNull();
        assertThat(result.history().getFirst().targetName()).isEqualTo("홍*동");
        assertThat(result.history().getFirst().actorUuid()).isNull();
        assertThat(result.history().getFirst().actorName()).isEqualTo("김*리");
    }

    @Test void historyIsPagedNewestFirstAndSizeIsCapped() {
        StampEvent event = new StampEvent(booth, TARGET, ACTOR, StampAction.GRANT, StampMethod.QR, NOW);
        when(events.findAllByBoothId(org.mockito.ArgumentMatchers.eq(1L), any(Pageable.class)))
                .thenAnswer(invocation -> new PageImpl<>(java.util.Collections.nCopies(50, event),
                        invocation.getArgument(1), 250));
        when(profiles.getProfiles(any())).thenReturn(List.of(
                profile(TARGET, "홍길동", "2026000001"), profile(ACTOR, "김관리", "2026000002")));

        var result = service.historyAs(ACTOR, FestivalRole.ADMIN, 1L, 2, 500);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(events).findAllByBoothId(org.mockito.ArgumentMatchers.eq(1L), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(2);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(100);
        assertThat(pageable.getValue().getSort().toList())
                .extracting(org.springframework.data.domain.Sort.Order::getProperty)
                .containsExactly("occurredAt", "id");
        assertThat(result.historyPage()).isEqualTo(2);
        assertThat(result.historySize()).isEqualTo(100);
        assertThat(result.historyTotalElements()).isEqualTo(250);
        assertThat(result.historyTotalPages()).isEqualTo(3);
    }

    @Test void boothManagerCannotSeeHistoryForUnassignedBooth() {
        when(booths.existsByIdAndManagersUserUuid(1L, ACTOR)).thenReturn(false);
        assertThatThrownBy(() -> service.historyAs(ACTOR, FestivalRole.BOOTH_MANAGER, 1L))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.status().value()).isEqualTo(403));
    }

    private void authenticate(FestivalRole role) {
        MeResponse me = new MeResponse(TARGET, "user", "u@example.com", "USER", "ACTIVE", null, null,
                null, null, null, null, null, null, null, Set.of(role));
        when(users.getMe("access", "refresh")).thenReturn(new AuthorizedResult<>(me, null, null));
    }
    private InternalUserProfile profile(UUID uuid, String name, String studentNo) {
        return new InternalUserProfile(uuid, "user", "u@example.com", "USER", "ACTIVE", name, null,
                studentNo, "컴퓨터공학과", 1, "ENROLLED", null, null, null);
    }
}
