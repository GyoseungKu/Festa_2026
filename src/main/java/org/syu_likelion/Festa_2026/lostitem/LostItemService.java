package org.syu_likelion.Festa_2026.lostitem;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.syu_likelion.Festa_2026.error.ApiException;
import org.syu_likelion.Festa_2026.storage.TransactionalFileActions;
import org.syu_likelion.Festa_2026.lostitem.LostItemDtos.LostItemImageResponse;
import org.syu_likelion.Festa_2026.lostitem.LostItemDtos.LostItemMutationRequest;
import org.syu_likelion.Festa_2026.lostitem.LostItemDtos.LostItemPageResponse;
import org.syu_likelion.Festa_2026.lostitem.LostItemDtos.LostItemResponse;
import org.syu_likelion.Festa_2026.lostitem.LostItemImageStorage.StoredImage;

@Service
public class LostItemService {
    private static final int MAX_IMAGES = 5;
    private static final int MAX_PAGE_SIZE = 100;

    private final LostItemNoticeRepository repository;
    private final LostItemImageStorage storage;
    private final Clock clock;

    public LostItemService(LostItemNoticeRepository repository, LostItemImageStorage storage, Clock clock) {
        this.repository = repository;
        this.storage = storage;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public LostItemPageResponse listPublic(LostItemStatus status, LostItemSort order, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        LostItemSort safeOrder = order == null ? LostItemSort.NEWEST : order;
        PageRequest pageable = PageRequest.of(safePage, safeSize, sort(safeOrder));
        Page<LostItemNotice> notices = status == null
                ? repository.findAll(pageable)
                : repository.findByStatus(status, pageable);
        return new LostItemPageResponse(notices.getContent().stream().map(this::toResponse).toList(),
                notices.getNumber(), notices.getSize(), notices.getTotalElements(), notices.getTotalPages());
    }

    public LostItemPageResponse listPublic(LostItemStatus status, int page, int size) {
        return listPublic(status, LostItemSort.NEWEST, page, size);
    }

    @Transactional
    public LostItemResponse getPublic(Long id) {
        if (repository.incrementViewCount(id) == 0) throw notFound();
        return toResponse(find(id));
    }

    @Transactional(readOnly = true)
    public List<LostItemResponse> listAdmin(LostItemStatus status, LostItemSort order) {
        LostItemSort safeOrder = order == null ? LostItemSort.NEWEST : order;
        List<LostItemNotice> notices = status == null
                ? repository.findAll(sort(safeOrder))
                : repository.findByStatus(status, sort(safeOrder));
        return notices
                .stream().map(this::toResponse).toList();
    }

    public List<LostItemResponse> listAdmin() {
        return listAdmin(null, LostItemSort.NEWEST);
    }

    @Transactional(readOnly = true)
    public LostItemResponse getAdmin(Long id) {
        return toResponse(find(id));
    }

    @Transactional
    public LostItemResponse createAs(UUID actorUuid, String authorName, LostItemMutationRequest request,
                                     List<MultipartFile> imageFiles) {
        Normalized data = normalize(request);
        List<MultipartFile> files = nonEmptyFiles(imageFiles);
        requireImageLimit(files.size());
        String cleanAuthor = requiredText(authorName, 100, "작성자");
        LostItemNotice notice = new LostItemNotice(data.title(), data.content(), data.status(), data.pinned(),
                actorUuid, cleanAuthor, clock.instant());
        List<StoredImage> uploaded = new ArrayList<>();
        boolean rollbackCleanup = TransactionalFileActions.deleteOnRollback(() -> deleteStored(uploaded));
        try {
            addImages(notice, files, uploaded);
            return toResponse(repository.saveAndFlush(notice));
        } catch (RuntimeException failure) {
            if (!rollbackCleanup) deleteStored(uploaded);
            throw failure;
        }
    }

    @Transactional
    public LostItemResponse updateAs(Long id, UUID actorUuid, LostItemMutationRequest request,
                                     List<Long> removeImageIds, List<MultipartFile> imageFiles) {
        LostItemNotice notice = find(id);
        Normalized data = normalize(request);
        Set<Long> removals = new HashSet<>(removeImageIds == null ? List.of() : removeImageIds);
        Set<Long> existing = notice.getImages().stream().map(LostItemImage::getId)
                .collect(java.util.stream.Collectors.toSet());
        if (!existing.containsAll(removals)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_LOST_ITEM_IMAGE_ID",
                    "삭제할 사진을 확인해 주세요.");
        }
        List<MultipartFile> files = nonEmptyFiles(imageFiles);
        requireImageLimit(notice.getImages().size() - removals.size() + files.size());
        List<LostItemImage> removed = notice.getImages().stream()
                .filter(image -> removals.contains(image.getId())).toList();
        removed.forEach(notice::removeImage);
        notice.update(data.title(), data.content(), data.status(), data.pinned(), actorUuid, clock.instant());

        List<StoredImage> uploaded = new ArrayList<>();
        boolean rollbackCleanup = TransactionalFileActions.deleteOnRollback(() -> deleteStored(uploaded));
        try {
            addImages(notice, files, uploaded);
            LostItemResponse response = toResponse(repository.saveAndFlush(notice));
            TransactionalFileActions.deleteAfterCommit(() ->
                    removed.forEach(image -> storage.delete(image.getStorageKey())));
            return response;
        } catch (RuntimeException failure) {
            if (!rollbackCleanup) deleteStored(uploaded);
            throw failure;
        }
    }

    @Transactional
    public LostItemResponse changeStatusAs(Long id, UUID actorUuid, LostItemStatus status) {
        if (status == null) throw new ApiException(HttpStatus.BAD_REQUEST, "LOST_ITEM_STATUS_REQUIRED",
                "분실물 상태를 선택해 주세요.");
        LostItemNotice notice = find(id);
        notice.changeStatus(status, actorUuid);
        return toResponse(repository.saveAndFlush(notice));
    }

    @Transactional
    public LostItemResponse changePinnedAs(Long id, UUID actorUuid, boolean pinned) {
        LostItemNotice notice = find(id);
        notice.changePinned(pinned, actorUuid, clock.instant());
        return toResponse(repository.saveAndFlush(notice));
    }

    @Transactional
    public void deleteAs(Long id) {
        LostItemNotice notice = find(id);
        List<String> keys = notice.getImages().stream().map(LostItemImage::getStorageKey).toList();
        repository.delete(notice);
        repository.flush();
        TransactionalFileActions.deleteAfterCommit(() -> keys.forEach(storage::delete));
    }

    private LostItemNotice find(Long id) {
        return repository.findWithImagesById(id).orElseThrow(this::notFound);
    }

    private ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "LOST_ITEM_NOT_FOUND",
                "분실물 공지를 찾을 수 없습니다.");
    }

    private Normalized normalize(LostItemMutationRequest request) {
        if (request == null || request.status() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_LOST_ITEM", "필수 정보를 입력해 주세요.");
        }
        return new Normalized(requiredText(request.title(), 150, "제목"),
                requiredText(request.content(), 5000, "내용"), request.status(), request.pinned());
    }

    private String requiredText(String value, int maxLength, String field) {
        String cleaned = value == null ? "" : value.trim();
        if (cleaned.isEmpty() || cleaned.length() > maxLength) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_LOST_ITEM", field + "을(를) 확인해 주세요.");
        }
        return cleaned;
    }

    private List<MultipartFile> nonEmptyFiles(List<MultipartFile> files) {
        return files == null ? List.of() : files.stream()
                .filter(file -> file != null && !file.isEmpty()).toList();
    }

    private void requireImageLimit(int count) {
        if (count > MAX_IMAGES) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "LOST_ITEM_IMAGE_LIMIT_EXCEEDED",
                    "분실물 사진은 최대 5장까지 등록할 수 있습니다.");
        }
    }

    private void addImages(LostItemNotice notice, List<MultipartFile> files, List<StoredImage> uploaded) {
        for (MultipartFile file : files) {
            StoredImage image = storage.store(file);
            uploaded.add(image);
            notice.addImage(new LostItemImage(image.url(), image.storageKey(), image.originalFilename()));
        }
    }

    private void deleteStored(List<StoredImage> uploaded) {
        uploaded.forEach(image -> storage.delete(image.storageKey()));
    }

    private Sort sort(LostItemSort order) {
        Sort pinned = Sort.by(Sort.Order.desc("pinned"), Sort.Order.desc("pinnedAt"));
        Sort.Direction direction = order == LostItemSort.OLDEST ? Sort.Direction.ASC : Sort.Direction.DESC;
        return pinned.and(Sort.by(new Sort.Order(direction, "createdAt"),
                new Sort.Order(direction, "id")));
    }

    private LostItemResponse toResponse(LostItemNotice notice) {
        List<LostItemImageResponse> images = new ArrayList<>();
        for (int index = 0; index < notice.getImages().size(); index++) {
            LostItemImage image = notice.getImages().get(index);
            images.add(new LostItemImageResponse(image.getId(), image.getUrl(), index));
        }
        return new LostItemResponse(notice.getId(), notice.getTitle(), notice.getContent(), notice.getStatus(),
                notice.getStatus().getLabel(), notice.isPinned(), notice.getViewCount(), images,
                notice.getAuthorName(), notice.getCreatedAt(), notice.getUpdatedAt());
    }

    private record Normalized(String title, String content, LostItemStatus status, boolean pinned) { }
}
