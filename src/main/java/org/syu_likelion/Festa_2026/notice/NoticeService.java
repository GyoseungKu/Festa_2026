package org.syu_likelion.Festa_2026.notice;

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
import org.syu_likelion.Festa_2026.notice.NoticeDtos.NoticeAttachmentResponse;
import org.syu_likelion.Festa_2026.notice.NoticeDtos.NoticeMutationRequest;
import org.syu_likelion.Festa_2026.notice.NoticeDtos.NoticePageResponse;
import org.syu_likelion.Festa_2026.notice.NoticeDtos.NoticeResponse;
import org.syu_likelion.Festa_2026.notice.NoticeAttachmentStorage.StoredAttachment;

@Service
public class NoticeService {
    private static final int MAX_ATTACHMENTS = 10;
    private static final int MAX_PAGE_SIZE = 100;

    private final NoticeRepository repository;
    private final NoticeAttachmentStorage storage;
    private final Clock clock;

    public NoticeService(NoticeRepository repository, NoticeAttachmentStorage storage, Clock clock) {
        this.repository = repository;
        this.storage = storage;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public NoticePageResponse listPublic(NoticeSort order, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        NoticeSort safeOrder = order == null ? NoticeSort.NEWEST : order;
        PageRequest pageable = PageRequest.of(safePage, safeSize, sort(safeOrder));
        Page<Notice> notices = repository.findAll(pageable);
        return new NoticePageResponse(notices.getContent().stream().map(this::toResponse).toList(),
                notices.getNumber(), notices.getSize(), notices.getTotalElements(), notices.getTotalPages());
    }

    @Transactional(readOnly = true)
    public NoticePageResponse listPublic(int page, int size) {
        return listPublic(NoticeSort.NEWEST, page, size);
    }

    @Transactional
    public NoticeResponse getPublic(Long id) {
        if (repository.incrementViewCount(id) == 0) throw notFound();
        return toResponse(find(id));
    }

    @Transactional(readOnly = true)
    public List<NoticeResponse> listAdmin(NoticeSort order) {
        NoticeSort safeOrder = order == null ? NoticeSort.NEWEST : order;
        List<Notice> notices = repository.findAll(sort(safeOrder));
        return notices
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public NoticePageResponse listAdmin(NoticeSort order, int page, int size) {
        return listPublic(order, page, size);
    }

    @Transactional(readOnly = true)
    public List<NoticeResponse> listAdmin() {
        return listAdmin(NoticeSort.NEWEST);
    }

    @Transactional(readOnly = true)
    public NoticeResponse getAdmin(Long id) {
        return toResponse(find(id));
    }

    @Transactional
    public NoticeResponse createAs(UUID actorUuid, String authorName, NoticeMutationRequest request,
                                     List<MultipartFile> attachmentFiles) {
        Normalized data = normalize(request);
        List<MultipartFile> files = nonEmptyFiles(attachmentFiles);
        requireAttachmentLimit(files.size());
        String cleanAuthor = requiredText(authorName, 100, "작성자");
        Notice notice = new Notice(data.title(), data.content(), data.pinned(),
                actorUuid, cleanAuthor, clock.instant());
        List<StoredAttachment> uploaded = new ArrayList<>();
        boolean rollbackCleanup = TransactionalFileActions.deleteOnRollback(() -> deleteStored(uploaded));
        try {
            addAttachments(notice, files, uploaded);
            return toResponse(repository.saveAndFlush(notice));
        } catch (RuntimeException failure) {
            if (!rollbackCleanup) deleteStored(uploaded);
            throw failure;
        }
    }

    @Transactional
    public NoticeResponse updateAs(Long id, UUID actorUuid, NoticeMutationRequest request,
                                     List<Long> removeAttachmentIds, List<MultipartFile> attachmentFiles) {
        Notice notice = find(id);
        Normalized data = normalize(request);
        Set<Long> removals = new HashSet<>(removeAttachmentIds == null ? List.of() : removeAttachmentIds);
        Set<Long> existing = notice.getAttachments().stream().map(NoticeAttachment::getId)
                .collect(java.util.stream.Collectors.toSet());
        if (!existing.containsAll(removals)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_NOTICE_ATTACHMENT_ID",
                    "삭제할 첨부파일을 확인해 주세요.");
        }
        List<MultipartFile> files = nonEmptyFiles(attachmentFiles);
        requireAttachmentLimit(notice.getAttachments().size() - removals.size() + files.size());
        List<NoticeAttachment> removed = notice.getAttachments().stream()
                .filter(attachment -> removals.contains(attachment.getId())).toList();
        removed.forEach(notice::removeAttachment);
        notice.update(data.title(), data.content(), data.pinned(), actorUuid, clock.instant());

        List<StoredAttachment> uploaded = new ArrayList<>();
        boolean rollbackCleanup = TransactionalFileActions.deleteOnRollback(() -> deleteStored(uploaded));
        try {
            addAttachments(notice, files, uploaded);
            NoticeResponse response = toResponse(repository.saveAndFlush(notice));
            TransactionalFileActions.deleteAfterCommit(() ->
                    removed.forEach(attachment -> storage.delete(attachment.getStorageKey())));
            return response;
        } catch (RuntimeException failure) {
            if (!rollbackCleanup) deleteStored(uploaded);
            throw failure;
        }
    }

    @Transactional
    public NoticeResponse changePinnedAs(Long id, UUID actorUuid, boolean pinned) {
        Notice notice = find(id);
        notice.changePinned(pinned, actorUuid, clock.instant());
        return toResponse(repository.saveAndFlush(notice));
    }

    @Transactional
    public void deleteAs(Long id) {
        Notice notice = find(id);
        List<String> keys = notice.getAttachments().stream().map(NoticeAttachment::getStorageKey).toList();
        repository.delete(notice);
        repository.flush();
        TransactionalFileActions.deleteAfterCommit(() -> keys.forEach(storage::delete));
    }

    private Notice find(Long id) {
        return repository.findWithAttachmentsById(id).orElseThrow(this::notFound);
    }

    private ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "NOTICE_NOT_FOUND",
                "일반 공지를 찾을 수 없습니다.");
    }

    private Normalized normalize(NoticeMutationRequest request) {
        if (request == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_NOTICE", "필수 정보를 입력해 주세요.");
        }
        return new Normalized(requiredText(request.title(), 150, "제목"),
                requiredText(request.content(), 5000, "내용"), request.pinned());
    }

    private String requiredText(String value, int maxLength, String field) {
        String cleaned = value == null ? "" : value.trim();
        if (cleaned.isEmpty() || cleaned.length() > maxLength) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_NOTICE", field + "을(를) 확인해 주세요.");
        }
        return cleaned;
    }

    private List<MultipartFile> nonEmptyFiles(List<MultipartFile> files) {
        return files == null ? List.of() : files.stream()
                .filter(file -> file != null && !file.isEmpty()).toList();
    }

    private void requireAttachmentLimit(int count) {
        if (count > MAX_ATTACHMENTS) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "NOTICE_ATTACHMENT_LIMIT_EXCEEDED",
                    "공지 첨부파일은 최대 10개까지 등록할 수 있습니다.");
        }
    }

    private void addAttachments(Notice notice, List<MultipartFile> files, List<StoredAttachment> uploaded) {
        for (MultipartFile file : files) {
            StoredAttachment attachment = storage.store(file);
            uploaded.add(attachment);
            notice.addAttachment(new NoticeAttachment(attachment.url(), attachment.storageKey(), attachment.originalFilename(), attachment.contentType(), attachment.size()));
        }
    }

    private void deleteStored(List<StoredAttachment> uploaded) {
        uploaded.forEach(attachment -> storage.delete(attachment.storageKey()));
    }

    private Sort sort(NoticeSort order) {
        Sort pinned = Sort.by(Sort.Order.desc("pinned"), Sort.Order.desc("pinnedAt"));
        Sort.Direction direction = order == NoticeSort.OLDEST ? Sort.Direction.ASC : Sort.Direction.DESC;
        return pinned.and(Sort.by(new Sort.Order(direction, "createdAt"),
                new Sort.Order(direction, "id")));
    }

    private NoticeResponse toResponse(Notice notice) {
        List<NoticeAttachmentResponse> media = new ArrayList<>();
        List<NoticeAttachmentResponse> files = new ArrayList<>();
        for (NoticeAttachment attachment : notice.getAttachments()) {
            String type = attachment.getContentType();
            List<NoticeAttachmentResponse> target = type.startsWith("image/") || type.startsWith("video/")
                    ? media : files;
            target.add(new NoticeAttachmentResponse(attachment.getId(), attachment.getUrl(),
                    attachment.getOriginalFilename(), type, attachment.getSize(), target.size()));
        }
        return new NoticeResponse(notice.getId(), notice.getTitle(), notice.getContent(), notice.isPinned(),
                notice.getViewCount(), List.copyOf(media), List.copyOf(files),
                notice.getAuthorName(), notice.getCreatedAt(), notice.getUpdatedAt());
    }

    private record Normalized(String title, String content, boolean pinned) { }
}
