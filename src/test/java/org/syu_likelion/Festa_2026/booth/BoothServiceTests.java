package org.syu_likelion.Festa_2026.booth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.syu_likelion.Festa_2026.auth.AuthorizedSsoExecutor.AuthorizedResult;
import org.syu_likelion.Festa_2026.booth.BoothDtos.BoothMutationRequest;
import org.syu_likelion.Festa_2026.booth.BoothDtos.BoothMediaOrderRequest;
import org.syu_likelion.Festa_2026.error.ApiException;
import org.syu_likelion.Festa_2026.user.FestivalRole;
import org.syu_likelion.Festa_2026.user.FestivalUser;
import org.syu_likelion.Festa_2026.user.FestivalUserRepository;
import org.syu_likelion.Festa_2026.user.UserDtos.MeResponse;
import org.syu_likelion.Festa_2026.user.UserService;
import org.springframework.test.util.ReflectionTestUtils;

class BoothServiceTests {
    private static final UUID USER_ID = UUID.fromString("123e4567-e89b-12d3-a456-426614174001");
    private static final UUID MANAGER_1 = UUID.fromString("123e4567-e89b-12d3-a456-426614174002");
    private static final UUID MANAGER_2 = UUID.fromString("123e4567-e89b-12d3-a456-426614174003");
    private FestivalBoothRepository booths;
    private BoothFavoriteRepository favorites;
    private FestivalUserRepository festivalUsers;
    private UserService users;
    private BoothMediaStorage storage;
    private BoothService service;

    @BeforeEach void setUp() {
        booths = mock(FestivalBoothRepository.class); favorites = mock(BoothFavoriteRepository.class);
        festivalUsers = mock(FestivalUserRepository.class); users = mock(UserService.class);
        storage = mock(BoothMediaStorage.class);
        service = new BoothService(booths, favorites, festivalUsers, users, storage);
    }

    @Test void anonymousUserCanSeeEveryBoothWithoutFavoriteCounts() {
        when(booths.findAllByOrderByNameAsc()).thenReturn(List.of(entity(Set.of())));
        var result = service.list(null, null);
        assertThat(result.body()).hasSize(1);
        assertThat(result.body().getFirst().category()).isEqualTo(BoothCategory.PHOTO_BOOTH);
        assertThat(result.body().getFirst().name()).isEqualTo("멋사 부스");
        assertThat(result.body().getFirst().stampEnabled()).isTrue();
        assertThat(result.body().getFirst().favorited()).isFalse();
        verify(users, never()).getMe(any(), any());
    }

    @Test void loggedInUserReceivesOwnFavoriteStateOnly() {
        authenticate(FestivalRole.USER);
        FestivalBooth booth = entity(Set.of());
        when(booths.findAllByOrderByNameAsc()).thenReturn(List.of(booth));
        when(favorites.findBoothIdsByUserUuid(USER_ID)).thenReturn(java.util.Collections.singletonList(null));
        var result = service.list("access", "refresh");
        assertThat(result.body().getFirst().favorited()).isTrue();
    }

    @Test void adminCanAssignMultipleManagersAndRoleIsGranted() {
        FestivalUser first = new FestivalUser(MANAGER_1);
        FestivalUser second = new FestivalUser(MANAGER_2);
        when(festivalUsers.findAllByUserUuidIn(List.of(MANAGER_1, MANAGER_2))).thenReturn(List.of(first, second));
        when(booths.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var result = service.createAs(USER_ID, request(List.of(MANAGER_1, MANAGER_2)), List.of(), List.of());
        assertThat(result.booth().category()).isEqualTo(BoothCategory.FOOD_TRUCK);
        assertThat(result.managerUuids()).containsExactlyInAnyOrder(MANAGER_1, MANAGER_2);
        assertThat(first.getRoles()).contains(FestivalRole.BOOTH_MANAGER);
        assertThat(second.getRoles()).contains(FestivalRole.BOOTH_MANAGER);
    }

    @Test void categoryUpdateIsVisibleInDetailAndFavoriteList() {
        FestivalBooth booth = entity(Set.of());
        when(booths.findById(7L)).thenReturn(java.util.Optional.of(booth));
        when(booths.saveAndFlush(booth)).thenReturn(booth);

        var updated = service.updateAs(7L, USER_ID, request(List.of()));
        assertThat(updated.booth().category()).isEqualTo(BoothCategory.FOOD_TRUCK);
        assertThat(service.detail(7L, null, null).body().category()).isEqualTo(BoothCategory.FOOD_TRUCK);

        authenticate(FestivalRole.USER);
        when(favorites.findBoothsByUserUuid(USER_ID)).thenReturn(List.of(booth));
        var favorite = service.myFavorites("access", "refresh").body().getFirst();
        assertThat(favorite.category()).isEqualTo(BoothCategory.FOOD_TRUCK);
        assertThat(favorite.favorited()).isTrue();
    }

    @Test void categoryIsRequiredForMutation() {
        BoothMutationRequest valid = request(List.of());
        BoothMutationRequest missing = new BoothMutationRequest(valid.latitude(), valid.longitude(),
                valid.name(), valid.operator(), valid.description(), valid.opensAt(), valid.closesAt(),
                valid.stampEnabled(), valid.managerUuids(), null);
        assertThatThrownBy(() -> service.createAs(USER_ID, missing, List.of(), List.of()))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("INVALID_BOOTH"));
        verify(booths, never()).saveAndFlush(any());
    }

    @Test void ordinaryUserCannotUseManagementApi() {
        authenticate(FestivalRole.USER);
        assertThatThrownBy(() -> service.create("access", "refresh", request(List.of())))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("BOOTH_MANAGE_FORBIDDEN"));
        verify(booths, never()).saveAndFlush(any());
    }

    @Test void imageLimitIsFive() {
        when(festivalUsers.findAllByUserUuidIn(List.of())).thenReturn(List.of());
        List<MockMultipartFile> images = java.util.stream.IntStream.range(0, 6)
                .mapToObj(i -> new MockMultipartFile("files", i + ".jpg", "image/jpeg", new byte[]{1})).toList();
        assertThatThrownBy(() -> service.createAs(USER_ID, request(List.of()), List.copyOf(images), List.of()))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("TOO_MANY_BOOTH_MEDIA"));
        verify(storage, never()).store(any(), any());
    }

    @Test void closingTimeMustBeLaterThanOpeningTime() {
        BoothMutationRequest invalid = new BoothMutationRequest(BigDecimal.valueOf(37.64), BigDecimal.valueOf(127.1),
                "부스", "운영자", "설명", LocalTime.NOON, LocalTime.NOON, true, List.of(), BoothCategory.GENERAL);
        assertThatThrownBy(() -> service.createAs(USER_ID, invalid, List.of(), List.of()))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("INVALID_BOOTH_HOURS"));
    }

    @Test void mediaOrderUsesLockedBoothAndPersistsIntegratedOrderAndRepresentative() {
        FestivalBooth booth = entity(Set.of());
        BoothMedia first = new BoothMedia(BoothMediaKind.IMAGE, "https://cdn/1", "one", "1.jpg");
        BoothMedia second = new BoothMedia(BoothMediaKind.VIDEO, "https://cdn/2", "two", "2.mp4");
        ReflectionTestUtils.setField(first, "id", 1L);
        ReflectionTestUtils.setField(second, "id", 2L);
        booth.addMedia(first);
        booth.addMedia(second);
        when(booths.findByIdForUpdate(7L)).thenReturn(java.util.Optional.of(booth));
        when(booths.saveAndFlush(booth)).thenReturn(booth);

        var result = service.orderMediaAs(7L, new BoothMediaOrderRequest(List.of(2L, 1L), 2L));

        verify(booths).findByIdForUpdate(7L);
        assertThat(result.booth().media()).extracting(item -> item.id()).containsExactly(2L, 1L);
        assertThat(result.booth().representativeMedia().id()).isEqualTo(2L);
    }

    private BoothMutationRequest request(List<UUID> managers) {
        return new BoothMutationRequest(new BigDecimal("37.6432000"), new BigDecimal("127.1059000"),
                "멋사 부스", "멋쟁이사자처럼", "부스 설명", LocalTime.of(10, 0), LocalTime.of(18, 0), true, managers, BoothCategory.FOOD_TRUCK);
    }
    private FestivalBooth entity(Set<FestivalUser> managers) {
        return new FestivalBooth(new BigDecimal("37.6432000"), new BigDecimal("127.1059000"), "멋사 부스",
                "멋쟁이사자처럼", "부스 설명", LocalTime.of(10, 0), LocalTime.of(18, 0), true, managers, USER_ID, BoothCategory.PHOTO_BOOTH);
    }
    private void authenticate(FestivalRole role) {
        MeResponse me = new MeResponse(USER_ID, "user", "user@example.com", "USER", "ACTIVE", null, null,
                null, null, null, null, null, null, null, Set.of(role));
        when(users.getMe("access", "refresh")).thenReturn(new AuthorizedResult<>(me, null, null));
    }
}
