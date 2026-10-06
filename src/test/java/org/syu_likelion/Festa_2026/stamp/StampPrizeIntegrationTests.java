package org.syu_likelion.Festa_2026.stamp;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.syu_likelion.Festa_2026.booth.*;
import org.syu_likelion.Festa_2026.error.ApiException;
import org.syu_likelion.Festa_2026.qr.QrService;
import org.syu_likelion.Festa_2026.sso.SsoInternalProfileClient;
import org.syu_likelion.Festa_2026.sso.SsoProfiles.InternalUserProfile;
import org.syu_likelion.Festa_2026.user.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:stamp-prizes;MODE=MySQL;DB_CLOSE_DELAY=-1")
class StampPrizeIntegrationTests {
    @Autowired StampPrizeService prizes;
    @Autowired StampPrizeRepository prizeRepository;
    @Autowired StampService stamps;
    @Autowired BoothService booths;
    @Autowired BoothStampRepository stampRepository;
    @Autowired FestivalBoothRepository boothRepository;
    @Autowired FestivalUserRepository users;
    @MockitoBean QrService qr;
    @MockitoBean SsoInternalProfileClient profiles;
    UUID target;
    UUID actor;

    @BeforeEach void setUp() {
        target = UUID.randomUUID(); actor = UUID.randomUUID();
        users.saveAndFlush(new FestivalUser(target));
        when(qr.resolveUserUuid("token")).thenReturn(target);
        when(profiles.getProfile(target)).thenReturn(new InternalUserProfile(target, "user", "u@example.com",
                "USER", "ACTIVE", "홍길동", null, "2026000001", "컴퓨터공학과", 1, "ENROLLED", null, null, null));
    }

    @ParameterizedTest @ValueSource(ints = {0, 5, 6})
    void eligibilityDependsOnCurrentStampCountAndPersistsGrant(int count) {
        addStamps(count);
        var lookup = prizes.lookupQrAs(FestivalRole.ADMIN, "token");
        assertThat(lookup.stampCount()).isEqualTo(count);
        assertThat(lookup.eligible()).isEqualTo(count >= 6);
        assertThat(lookup.prizeGranted()).isFalse();
        if (count < 6) {
            assertThatThrownBy(() -> prizes.grantQrAs(actor, FestivalRole.ADMIN, "token"))
                    .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("STAMP_PRIZE_NOT_READY"));
            assertThat(prizeRepository.findByTargetUserUuid(target)).isEmpty();
        } else {
            var granted = prizes.grantQrAs(actor, FestivalRole.SUPER_ADMIN, "token");
            assertThat(granted.prizeGranted()).isTrue();
            assertThat(granted.eligible()).isFalse();
            assertThat(granted.stampCount()).isEqualTo(count);
            assertThat(granted.prizeGrantedBy()).isEqualTo(actor);
            var saved = prizeRepository.findByTargetUserUuid(target).orElseThrow();
            assertThat(saved.getStampCount()).isEqualTo(count);
            assertThat(saved.getGrantedAt()).isNotNull();
            assertThat(prizes.lookupQrAs(FestivalRole.ADMIN, "token").prizeGranted()).isTrue();
        }
    }

    @Test void revokingAfterPreviewRechecksEligibilityAndPrizeDoesNotResetAfterRegrant() {
        List<Long> ids = addStamps(6);
        assertThat(prizes.lookupQrAs(FestivalRole.ADMIN, "token").eligible()).isTrue();
        stamps.revokeBySearchAs(actor, ids.getFirst(), target);
        assertThatThrownBy(() -> prizes.grantQrAs(actor, FestivalRole.ADMIN, "token"))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("STAMP_PRIZE_NOT_READY"));
        stamps.grantBySearchAs(actor, ids.getFirst(), target);
        prizes.grantQrAs(actor, FestivalRole.ADMIN, "token");
        stamps.revokeBySearchAs(actor, ids.getFirst(), target);
        stamps.grantBySearchAs(actor, ids.getFirst(), target);
        assertThatThrownBy(() -> prizes.grantQrAs(actor, FestivalRole.ADMIN, "token"))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("STAMP_PRIZE_ALREADY_GRANTED"));
    }

    @Test void lowerRolesCannotReadOrGrantPrizes() {
        for (FestivalRole role : List.of(FestivalRole.USER, FestivalRole.STAFF, FestivalRole.BOOTH_MANAGER)) {
            assertThatThrownBy(() -> prizes.lookupQrAs(role, "token"))
                    .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.status().value()).isEqualTo(403));
            assertThatThrownBy(() -> prizes.grantQrAs(actor, role, "token"))
                    .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.status().value()).isEqualTo(403));
        }
        verify(qr, never()).resolveUserUuid(any());
    }

    @Test void expiredQrCannotGrantPrize() {
        addStamps(6);
        when(qr.resolveUserUuid("token")).thenThrow(new ApiException(org.springframework.http.HttpStatus.NOT_FOUND,
                "QR_TOKEN_NOT_FOUND", "만료된 QR입니다."));
        assertThatThrownBy(() -> prizes.grantQrAs(actor, FestivalRole.ADMIN, "token")).isInstanceOf(ApiException.class);
        assertThat(prizeRepository.findByTargetUserUuid(target)).isEmpty();
    }

    @Test void concurrentGrantsProduceOnlyOnePrize() throws Exception {
        addStamps(6);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Callable<String> grant = () -> {
                start.await();
                try { prizes.grantQrAs(actor, FestivalRole.ADMIN, "token"); return "granted"; }
                catch (ApiException conflict) { return conflict.code(); }
            };
            Future<String> first = pool.submit(grant);
            Future<String> second = pool.submit(grant);
            start.countDown();
            assertThat(List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("granted", "STAMP_PRIZE_ALREADY_GRANTED");
        }
    }

    @Test void directLookupGrantRevokeAndReissueKeepAuditHistory() {
        addStamps(6);
        assertThat(prizes.lookupUserAs(FestivalRole.ADMIN, target).eligible()).isTrue();
        prizes.grantUserAs(actor, FestivalRole.ADMIN, target);
        var first = prizeRepository.findByTargetUserUuid(target).orElseThrow();
        Long id = first.getId();
        assertThat(prizes.historyAs(FestivalRole.ADMIN, id, 0).getContent().getFirst().method())
                .isEqualTo(StampMethod.ADMIN_SEARCH);
        long originalVersion = first.getVersion();
        assertThatThrownBy(() -> prizes.revokeAs(actor, FestivalRole.ADMIN, id, originalVersion, " "))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("INVALID_STAMP_PRIZE_REASON"));
        prizes.revokeAs(actor, FestivalRole.ADMIN, id, originalVersion, "오지급 회수");
        assertThat(prizes.detailAs(FestivalRole.ADMIN, id).issued()).isFalse();
        assertThat(prizes.lookupUserAs(FestivalRole.ADMIN, target).eligible()).isTrue();
        prizes.grantQrAs(actor, FestivalRole.ADMIN, "token");
        assertThat(prizeRepository.findByTargetUserUuid(target).orElseThrow().getId()).isEqualTo(id);
        assertThatThrownBy(() -> prizes.revokeAs(actor, FestivalRole.ADMIN, id, originalVersion, "오래된 화면"))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("STAMP_PRIZE_CHANGED"));
        var events = prizes.historyAs(FestivalRole.ADMIN, id, 0).getContent();
        assertThat(events).extracting(StampPrizeService.EventView::action)
                .containsExactly(StampAction.GRANT, StampAction.REVOKE, StampAction.GRANT);
        assertThat(events.get(1).reason()).isEqualTo("오지급 회수");
    }

    @Test void directManagementRequiresAdminAndStillChecksSixStamps() {
        assertThatThrownBy(() -> prizes.grantUserAs(actor, FestivalRole.ADMIN, target))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("STAMP_PRIZE_NOT_READY"));
        for (FestivalRole role : List.of(FestivalRole.USER, FestivalRole.STAFF, FestivalRole.BOOTH_MANAGER)) {
            assertThatThrownBy(() -> prizes.searchAs(role, "홍길동", 0)).isInstanceOf(ApiException.class);
            assertThatThrownBy(() -> prizes.lookupUserAs(role, target)).isInstanceOf(ApiException.class);
            assertThatThrownBy(() -> prizes.grantUserAs(actor, role, target)).isInstanceOf(ApiException.class);
            assertThatThrownBy(() -> prizes.listAs(role, 0)).isInstanceOf(ApiException.class);
            assertThatThrownBy(() -> prizes.revokeAs(actor, role, 1L, 0, "오지급")).isInstanceOf(ApiException.class);
        }
    }

    @Test void legacyPrizeGetsOriginalGrantEventWhenRevoked() {
        var old = prizeRepository.saveAndFlush(new StampPrize(target, actor, java.time.Instant.now(), 6));
        assertThat(prizes.historyAs(FestivalRole.ADMIN, old.getId(), 0).getTotalElements()).isEqualTo(1);
        prizes.revokeAs(actor, FestivalRole.ADMIN, old.getId(), old.getVersion(), "기존 지급 철회");
        assertThat(prizes.historyAs(FestivalRole.ADMIN, old.getId(), 0).getTotalElements()).isEqualTo(2);
    }

    @Test void sixthGeneralStampIsAcceptedAndSeventhIsRejectedForBothMethods() {
        for (int i = 0; i < 5; i++) stamps.grantBySearchAs(actor, createBooth(BoothCategory.GENERAL), target);
        Long general = createBooth(BoothCategory.GENERAL);
        stamps.grantBySearchAs(actor, general, target);
        assertThat(prizes.lookupUserAs(FestivalRole.ADMIN, target).eligible()).isTrue();
        stamps.revokeBySearchAs(actor, general, target);
        stamps.grantQrAs(actor, FestivalRole.ADMIN, general, "token");
        assertThat(prizes.lookupUserAs(FestivalRole.ADMIN, target).eligible()).isTrue();
        assertThatThrownBy(() -> stamps.grantBySearchAs(actor, createBooth(BoothCategory.GENERAL), target))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("STAMP_BOARD_FULL"));
        assertThatThrownBy(() -> stamps.grantQrAs(actor, FestivalRole.ADMIN, createBooth(BoothCategory.EXTERNAL), "token"))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("STAMP_BOARD_FULL"));
        assertThat(stampRepository.findAllByUserUserUuidOrderByGrantedAtAsc(target)).hasSize(6);
    }

    @Test void existingBoardWithoutExternalCanRedeemPrize() {
        var user = users.findByUserUuid(target).orElseThrow();
        for (int i = 0; i < 6; i++) {
            var booth = boothRepository.findById(createBooth(BoothCategory.GENERAL)).orElseThrow();
            stampRepository.saveAndFlush(new BoothStamp(booth, user, java.time.Instant.now(), actor, StampMethod.ADMIN_SEARCH));
        }
        assertThat(prizes.lookupUserAs(FestivalRole.ADMIN, target).eligible()).isTrue();
        prizes.grantUserAs(actor, FestivalRole.ADMIN, target);
        assertThat(prizes.lookupUserAs(FestivalRole.ADMIN, target).prizeGranted()).isTrue();

    }

    @Test void concurrentStampGrantsCannotExceedSix() throws Exception {
        addStamps(5);
        Long firstBooth = createBooth(BoothCategory.GENERAL);
        Long secondBooth = createBooth(BoothCategory.EXTERNAL);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            var results = new java.util.ArrayList<Future<String>>();
            for (Long id : List.of(firstBooth, secondBooth)) results.add(pool.submit(() -> {
                start.await();
                try { stamps.grantBySearchAs(actor, id, target); return "granted"; }
                catch (ApiException conflict) { return conflict.code(); }
            }));
            start.countDown();
            assertThat(List.of(results.get(0).get(15, TimeUnit.SECONDS), results.get(1).get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("granted", "STAMP_BOARD_FULL");
        }
        assertThat(stampRepository.findAllByUserUserUuidOrderByGrantedAtAsc(target)).hasSize(6);
    }

    private Long createBooth(BoothCategory category) {
        return booths.createAs(actor, new BoothDtos.BoothMutationRequest(BigDecimal.ZERO, BigDecimal.ZERO,
                "부스", "운영팀", "설명", LocalTime.of(10, 0), LocalTime.of(18, 0), true, List.of(), category),
                List.of(), List.of()).booth().id();
    }

    private List<Long> addStamps(int count) {
        var ids = new java.util.ArrayList<Long>();
        for (int i = 0; i < count; i++) {
            var request = new BoothDtos.BoothMutationRequest(BigDecimal.ZERO, BigDecimal.ZERO, "부스 " + i,
                    "운영팀", "설명", LocalTime.of(10, 0), LocalTime.of(18, 0), true, List.of(), i == 0 ? BoothCategory.EXTERNAL : BoothCategory.GENERAL);
            Long id = booths.createAs(actor, request, List.of(), List.of()).booth().id();
            stamps.grantBySearchAs(actor, id, target);
            ids.add(id);
        }
        return ids;
    }
}
