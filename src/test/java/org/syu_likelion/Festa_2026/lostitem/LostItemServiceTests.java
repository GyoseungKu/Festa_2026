package org.syu_likelion.Festa_2026.lostitem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.mock.web.MockMultipartFile;
import org.syu_likelion.Festa_2026.error.ApiException;
import org.syu_likelion.Festa_2026.lostitem.LostItemDtos.LostItemMutationRequest;

class LostItemServiceTests {
    private static final Instant NOW = Instant.parse("2026-08-13T03:00:00Z");
    private static final UUID STAFF_UUID = UUID.fromString("123e4567-e89b-12d3-a456-426614174010");

    private LostItemNoticeRepository repository;
    private LostItemImageStorage storage;
    private LostItemService service;

    @BeforeEach
    void setUp() {
        repository = mock(LostItemNoticeRepository.class);
        storage = mock(LostItemImageStorage.class);
        service = new LostItemService(repository, storage, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void publicDetailIncrementsViewCountBeforeReturningNotice() {
        LostItemNotice notice = notice();
        when(repository.incrementViewCount(7L)).thenReturn(1);
        when(repository.findWithImagesById(7L)).thenReturn(Optional.of(notice));

        var response = service.getPublic(7L);

        assertThat(response.title()).isEqualTo("검은색 지갑");
        verify(repository).incrementViewCount(7L);
        verify(repository).findWithImagesById(7L);
    }

    @Test
    void missingPublicDetailDoesNotTryToLoadNoticeAfterFailedIncrement() {
        when(repository.incrementViewCount(99L)).thenReturn(0);

        assertThatThrownBy(() -> service.getPublic(99L))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.code()).isEqualTo("LOST_ITEM_NOT_FOUND"));
        verify(repository, never()).findWithImagesById(99L);
    }

    @Test
    void createRejectsMoreThanFiveImagesBeforeUpload() {
        List<MockMultipartFile> files = java.util.stream.IntStream.range(0, 6)
                .mapToObj(index -> new MockMultipartFile("imageFiles", index + ".webp",
                        "image/webp", new byte[]{1})).toList();

        assertThatThrownBy(() -> service.createAs(STAFF_UUID, "축제 스태프", request(), List.copyOf(files)))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.code()).isEqualTo("LOST_ITEM_IMAGE_LIMIT_EXCEEDED"));
        verify(storage, never()).store(any());
    }

    @Test
    void createUsesHoldingAsProvidedAndStoresAuthorSnapshot() {
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.createAs(STAFF_UUID, "축제 스태프", request(), List.of());

        assertThat(response.status()).isEqualTo(LostItemStatus.HOLDING);
        assertThat(response.authorName()).isEqualTo("축제 스태프");
        assertThat(response.viewCount()).isZero();
    }

    @Test
    void publicListAppliesStatusAndOldestSortWithPinnedItemsFirst() {
        when(repository.findByStatus(any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        service.listPublic(LostItemStatus.HOLDING, LostItemSort.OLDEST, 0, 20);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findByStatus(org.mockito.ArgumentMatchers.eq(LostItemStatus.HOLDING),
                pageable.capture());
        List<org.springframework.data.domain.Sort.Order> orders = pageable.getValue().getSort().toList();
        assertThat(orders).extracting(org.springframework.data.domain.Sort.Order::getProperty)
                .containsExactly("pinned", "pinnedAt", "createdAt", "id");
        assertThat(orders).extracting(org.springframework.data.domain.Sort.Order::getDirection)
                .containsExactly(org.springframework.data.domain.Sort.Direction.DESC,
                        org.springframework.data.domain.Sort.Direction.DESC,
                        org.springframework.data.domain.Sort.Direction.ASC,
                        org.springframework.data.domain.Sort.Direction.ASC);
    }

    private LostItemMutationRequest request() {
        return new LostItemMutationRequest("검은색 지갑", "학생회관 앞에서 발견했습니다.",
                LostItemStatus.HOLDING, false);
    }

    private LostItemNotice notice() {
        return new LostItemNotice("검은색 지갑", "학생회관 앞에서 발견했습니다.",
                LostItemStatus.HOLDING, false, STAFF_UUID, "축제 스태프", NOW);
    }
}
