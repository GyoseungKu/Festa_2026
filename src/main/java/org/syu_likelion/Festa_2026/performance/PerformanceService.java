package org.syu_likelion.Festa_2026.performance;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.syu_likelion.Festa_2026.auth.AuthorizedSsoExecutor.AuthorizedResult;
import org.syu_likelion.Festa_2026.error.ApiException;
import org.syu_likelion.Festa_2026.storage.TransactionalFileActions;
import org.syu_likelion.Festa_2026.performance.MediaStorage.StoredFile;
import org.syu_likelion.Festa_2026.performance.PerformanceDtos.MediaResponse;
import org.syu_likelion.Festa_2026.performance.PerformanceDtos.PerformanceMutationRequest;
import org.syu_likelion.Festa_2026.performance.PerformanceDtos.PerformanceResponse;
import org.syu_likelion.Festa_2026.user.FestivalRole;
import org.syu_likelion.Festa_2026.user.UserDtos.MeResponse;
import org.syu_likelion.Festa_2026.user.UserService;

@Service
public class PerformanceService {
    private static final int MAX_ATTACHMENTS = 3;
    private final FestivalPerformanceRepository repository;
    private final UserService users;
    private final MediaStorage storage;
    private final Clock clock;

    public PerformanceService(FestivalPerformanceRepository repository, UserService users,
                              MediaStorage storage, Clock clock) {
        this.repository = repository;
        this.users = users;
        this.storage = storage;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AuthorizedResult<List<PerformanceResponse>> listVisible(String accessToken, String refreshToken) {
        AuthorizedResult<MeResponse> authenticated = users.getMe(accessToken, refreshToken);
        Instant now = clock.instant();
        List<PerformanceResponse> body = repository.findAllByOrderByStartsAtAsc()
                .stream().map(item -> toPublicResponse(item, now)).toList();
        return rotated(authenticated, body);
    }

    @Transactional(readOnly = true)
    public AuthorizedResult<PerformanceResponse> getVisible(Long id, String accessToken, String refreshToken) {
        AuthorizedResult<MeResponse> authenticated = users.getMe(accessToken, refreshToken);
        return rotated(authenticated, toPublicResponse(find(id), clock.instant()));
    }

    @Transactional
    public AuthorizedResult<PerformanceResponse> create(String accessToken, String refreshToken,
                                                        PerformanceMutationRequest request) {
        AuthorizedResult<MeResponse> authenticated = users.getMe(accessToken, refreshToken);
        requireManager(authenticated.body().festivalRoles());
        PerformanceResponse body = createAs(authenticated.body().userUuid(), request, List.of(), List.of());
        return rotated(authenticated, body);
    }

    @Transactional
    public AuthorizedResult<PerformanceResponse> update(Long id, String accessToken, String refreshToken,
                                                        PerformanceMutationRequest request) {
        AuthorizedResult<MeResponse> authenticated = users.getMe(accessToken, refreshToken);
        requireManager(authenticated.body().festivalRoles());
        PerformanceResponse body = updateAs(id, authenticated.body().userUuid(), request,
                List.of(), List.of(), List.of());
        return rotated(authenticated, body);
    }

    @Transactional
    public AuthorizedResult<PerformanceResponse> upload(Long id, PerformanceMediaKind kind,
                                                        List<MultipartFile> files,
                                                        String accessToken, String refreshToken) {
        AuthorizedResult<MeResponse> authenticated = users.getMe(accessToken, refreshToken);
        requireManager(authenticated.body().festivalRoles());
        PerformanceResponse body = addFilesAs(id, authenticated.body().userUuid(), kind, files);
        return rotated(authenticated, body);
    }

    @Transactional
    public AuthorizedResult<PerformanceResponse> deleteMedia(Long id, Long mediaId,
                                                             String accessToken, String refreshToken) {
        AuthorizedResult<MeResponse> authenticated = users.getMe(accessToken, refreshToken);
        requireManager(authenticated.body().festivalRoles());
        PerformanceResponse body = removeMediaAs(id, authenticated.body().userUuid(), List.of(mediaId));
        return rotated(authenticated, body);
    }

    @Transactional
    public AuthorizedResult<Void> delete(Long id, String accessToken, String refreshToken) {
        AuthorizedResult<MeResponse> authenticated = users.getMe(accessToken, refreshToken);
        requireManager(authenticated.body().festivalRoles());
        deleteAs(id);
        return rotated(authenticated, null);
    }

    @Transactional(readOnly = true)
    public List<PerformanceResponse> listAll() {
        return repository.findAllByOrderByStartsAtAsc().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public PerformanceResponse getAdmin(Long id) {
        return toResponse(find(id));
    }

    @Transactional
    public PerformanceResponse createAs(UUID actorUuid, PerformanceMutationRequest request,
                                        List<MultipartFile> imageFiles, List<MultipartFile> videoFiles) {
        Normalized data = normalize(request);
        List<MultipartFile> images = nonEmptyFiles(imageFiles);
        List<MultipartFile> videos = nonEmptyFiles(videoFiles);
        requireAttachmentLimit(data.imageUrls().size() + images.size(), PerformanceMediaKind.IMAGE);
        requireAttachmentLimit(data.videoUrls().size() + videos.size(), PerformanceMediaKind.VIDEO);

        FestivalPerformance performance = new FestivalPerformance(data.category(), data.teamName(),
                data.memberNames(), data.startsAt(), data.endsAt(), data.description(), data.links(),
                data.publishedAt(), actorUuid);
        addLinkedMedia(performance, PerformanceMediaKind.IMAGE, data.imageUrls());
        addLinkedMedia(performance, PerformanceMediaKind.VIDEO, data.videoUrls());
        List<StoredFile> uploaded = new ArrayList<>();
        boolean rollbackCleanup = TransactionalFileActions.deleteOnRollback(() -> deleteStored(uploaded));
        try {
            addUploadedMedia(performance, PerformanceMediaKind.IMAGE, images, uploaded);
            addUploadedMedia(performance, PerformanceMediaKind.VIDEO, videos, uploaded);
            return toResponse(repository.saveAndFlush(performance));
        } catch (RuntimeException failure) {
            if (!rollbackCleanup) deleteStored(uploaded);
            throw failure;
        }
    }

    @Transactional
    public PerformanceResponse updateAs(Long id, UUID actorUuid, PerformanceMutationRequest request,
                                        List<Long> removeMediaIds, List<MultipartFile> imageFiles,
                                        List<MultipartFile> videoFiles) {
        FestivalPerformance performance = find(id);
        Normalized data = normalize(request);
        Set<Long> removals = new HashSet<>(removeMediaIds == null ? List.of() : removeMediaIds);
        validateRemovalIds(performance, removals);
        List<MultipartFile> images = nonEmptyFiles(imageFiles);
        List<MultipartFile> videos = nonEmptyFiles(videoFiles);
        int remainingImages = remainingUploads(performance, PerformanceMediaKind.IMAGE, removals);
        int remainingVideos = remainingUploads(performance, PerformanceMediaKind.VIDEO, removals);
        requireAttachmentLimit(remainingImages + data.imageUrls().size() + images.size(), PerformanceMediaKind.IMAGE);
        requireAttachmentLimit(remainingVideos + data.videoUrls().size() + videos.size(), PerformanceMediaKind.VIDEO);

        List<PerformanceMedia> removed = performance.getMedia().stream()
                .filter(item -> item.getSource() == PerformanceMediaSource.LINK
                        || removals.contains(item.getId()))
                .toList();
        removed.forEach(performance::removeMedia);
        performance.updateDetails(data.category(), data.teamName(), data.memberNames(), data.startsAt(),
                data.endsAt(), data.description(), data.links(), data.publishedAt(), actorUuid);
        addLinkedMedia(performance, PerformanceMediaKind.IMAGE, data.imageUrls());
        addLinkedMedia(performance, PerformanceMediaKind.VIDEO, data.videoUrls());

        List<StoredFile> uploaded = new ArrayList<>();
        boolean rollbackCleanup = TransactionalFileActions.deleteOnRollback(() -> deleteStored(uploaded));
        try {
            addUploadedMedia(performance, PerformanceMediaKind.IMAGE, images, uploaded);
            addUploadedMedia(performance, PerformanceMediaKind.VIDEO, videos, uploaded);
            PerformanceResponse response = toResponse(repository.saveAndFlush(performance));
            TransactionalFileActions.deleteAfterCommit(() -> removed.stream()
                    .filter(item -> item.getSource() == PerformanceMediaSource.UPLOAD)
                    .forEach(item -> storage.delete(item.getStorageKey())));
            return response;
        } catch (RuntimeException failure) {
            if (!rollbackCleanup) deleteStored(uploaded);
            throw failure;
        }
    }

    @Transactional
    public PerformanceResponse addFilesAs(Long id, UUID actorUuid, PerformanceMediaKind kind,
                                          List<MultipartFile> files) {
        FestivalPerformance performance = find(id);
        List<MultipartFile> uploads = nonEmptyFiles(files);
        if (uploads.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "MEDIA_FILE_REQUIRED", "업로드할 파일을 선택해 주세요.");
        }
        long current = performance.getMedia().stream().filter(item -> item.getKind() == kind).count();
        requireAttachmentLimit((int) current + uploads.size(), kind);
        List<StoredFile> stored = new ArrayList<>();
        boolean rollbackCleanup = TransactionalFileActions.deleteOnRollback(() -> deleteStored(stored));
        try {
            addUploadedMedia(performance, kind, uploads, stored);
            performance.updateDetails(performance.getCategory(), performance.getTeamName(),
                    performance.getMemberNames(), performance.getStartsAt(), performance.getEndsAt(),
                    performance.getDescription(), performance.getLinks(), performance.getPublishedAt(), actorUuid);
            return toResponse(repository.saveAndFlush(performance));
        } catch (RuntimeException failure) {
            if (!rollbackCleanup) deleteStored(stored);
            throw failure;
        }
    }

    @Transactional
    public PerformanceResponse removeMediaAs(Long id, UUID actorUuid, List<Long> mediaIds) {
        FestivalPerformance performance = find(id);
        Set<Long> removals = new HashSet<>(mediaIds == null ? List.of() : mediaIds);
        validateRemovalIds(performance, removals);
        List<PerformanceMedia> removed = performance.getMedia().stream()
                .filter(item -> removals.contains(item.getId())).toList();
        removed.forEach(performance::removeMedia);
        performance.updateDetails(performance.getCategory(), performance.getTeamName(),
                performance.getMemberNames(), performance.getStartsAt(), performance.getEndsAt(),
                performance.getDescription(), performance.getLinks(), performance.getPublishedAt(), actorUuid);
        PerformanceResponse response = toResponse(repository.saveAndFlush(performance));
        TransactionalFileActions.deleteAfterCommit(() -> removed.stream()
                .filter(item -> item.getSource() == PerformanceMediaSource.UPLOAD)
                .forEach(item -> storage.delete(item.getStorageKey())));
        return response;
    }

    @Transactional
    public void deleteAs(Long id) {
        FestivalPerformance performance = find(id);
        List<String> storageKeys = performance.getMedia().stream()
                .filter(item -> item.getSource() == PerformanceMediaSource.UPLOAD)
                .map(PerformanceMedia::getStorageKey).toList();
        repository.delete(performance);
        repository.flush();
        TransactionalFileActions.deleteAfterCommit(() -> storageKeys.forEach(storage::delete));
    }

    private FestivalPerformance find(Long id) {
        return repository.findById(id).orElseThrow(this::notFound);
    }

    private ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "PERFORMANCE_NOT_FOUND", "공연 정보를 찾을 수 없습니다.");
    }

    private void requireManager(Set<FestivalRole> roles) {
        if (roles == null || (!roles.contains(FestivalRole.ADMIN) && !roles.contains(FestivalRole.SUPER_ADMIN))) {
            throw new ApiException(HttpStatus.FORBIDDEN, "PERFORMANCE_MANAGE_FORBIDDEN",
                    "공연 정보는 ADMIN 이상만 관리할 수 있습니다.");
        }
    }

    private Normalized normalize(PerformanceMutationRequest request) {
        if (request == null || request.category() == null || request.startsAt() == null
                || request.endsAt() == null || request.publishedAt() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PERFORMANCE", "필수 공연 정보를 입력해 주세요.");
        }
        String teamName = requiredText(request.teamName(), 150, "팀 이름");
        String description = requiredText(request.description(), 5000, "팀 설명");
        List<String> members = cleanTextList(request.memberNames(), 100, 100, "구성원 이름");
        if (members.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "MEMBER_REQUIRED", "구성원 이름을 한 명 이상 입력해 주세요.");
        }
        if (!request.endsAt().isAfter(request.startsAt())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PERFORMANCE_TIME",
                    "공연 종료일시는 시작일시보다 늦어야 합니다.");
        }
        return new Normalized(request.category(), teamName, members, request.startsAt(), request.endsAt(),
                description, cleanUrls(request.links(), "링크"), cleanUrls(request.imageUrls(), "이미지 링크"),
                cleanUrls(request.videoUrls(), "동영상 링크"), request.publishedAt());
    }

    private String requiredText(String value, int maxLength, String field) {
        String cleaned = value == null ? "" : value.trim();
        if (cleaned.isEmpty() || cleaned.length() > maxLength) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PERFORMANCE", field + "을(를) 확인해 주세요.");
        }
        return cleaned;
    }

    private List<String> cleanTextList(List<String> values, int maxItems, int maxLength, String field) {
        List<String> cleaned = values == null ? List.of() : values.stream()
                .filter(value -> value != null && !value.isBlank()).map(String::trim).distinct().toList();
        if (cleaned.size() > maxItems || cleaned.stream().anyMatch(value -> value.length() > maxLength)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PERFORMANCE", field + "을(를) 확인해 주세요.");
        }
        return cleaned;
    }

    private List<String> cleanUrls(List<String> values, String field) {
        List<String> cleaned = values == null ? List.of() : values.stream()
                .filter(value -> value != null && !value.isBlank()).map(String::trim).distinct().toList();
        if (cleaned.size() > MAX_ATTACHMENTS) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "TOO_MANY_LINKS", field + "는 최대 3개입니다.");
        }
        for (String value : cleaned) validateHttpUrl(value, field);
        return cleaned;
    }

    private void validateHttpUrl(String value, String field) {
        if (value.length() > 2048) throw invalidUrl(field);
        try {
            URI uri = new URI(value);
            if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null || uri.getHost().isBlank()) throw invalidUrl(field);
        } catch (URISyntaxException exception) {
            throw invalidUrl(field);
        }
    }

    private ApiException invalidUrl(String field) {
        return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_MEDIA_URL",
                field + "는 http 또는 https 주소여야 합니다.");
    }

    private List<MultipartFile> nonEmptyFiles(List<MultipartFile> files) {
        return files == null ? List.of() : files.stream()
                .filter(file -> file != null && !file.isEmpty()).toList();
    }

    private void requireAttachmentLimit(int count, PerformanceMediaKind kind) {
        if (count > MAX_ATTACHMENTS) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "TOO_MANY_MEDIA",
                    kind == PerformanceMediaKind.IMAGE
                            ? "이미지는 링크와 파일을 합해 최대 3개입니다."
                            : "동영상은 링크와 파일을 합해 최대 3개입니다.");
        }
    }

    private void addLinkedMedia(FestivalPerformance performance, PerformanceMediaKind kind, List<String> urls) {
        urls.forEach(url -> performance.addMedia(PerformanceMedia.linked(kind, url)));
    }

    private void addUploadedMedia(FestivalPerformance performance, PerformanceMediaKind kind,
                                  List<MultipartFile> files, List<StoredFile> uploaded) {
        for (MultipartFile file : files) {
            StoredFile stored = storage.store(file, kind);
            uploaded.add(stored);
            performance.addMedia(PerformanceMedia.uploaded(kind, stored.url(), stored.storageKey(),
                    stored.originalFilename()));
        }
    }

    private void deleteStored(List<StoredFile> stored) {
        stored.forEach(file -> storage.delete(file.storageKey()));
    }

    private void validateRemovalIds(FestivalPerformance performance, Set<Long> removals) {
        Set<Long> existing = performance.getMedia().stream().map(PerformanceMedia::getId)
                .filter(java.util.Objects::nonNull).collect(java.util.stream.Collectors.toSet());
        if (!existing.containsAll(removals)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_MEDIA_ID", "삭제할 첨부파일을 확인해 주세요.");
        }
    }

    private int remainingUploads(FestivalPerformance performance, PerformanceMediaKind kind, Set<Long> removals) {
        return (int) performance.getMedia().stream()
                .filter(item -> item.getKind() == kind && item.getSource() == PerformanceMediaSource.UPLOAD
                        && !removals.contains(item.getId())).count();
    }

    private PerformanceResponse toResponse(FestivalPerformance performance) {
        return toResponse(performance, !performance.getPublishedAt().isAfter(clock.instant()));
    }

    private PerformanceResponse toPublicResponse(FestivalPerformance performance, Instant now) {
        if (!performance.getPublishedAt().isAfter(now)) return toResponse(performance, true);
        return new PerformanceResponse(performance.getId(), performance.getCategory(),
                performance.getCategory().getLabel(), "TBA", List.of(),
                performance.getStartsAt(), performance.getEndsAt(), "", List.of(), List.of(), List.of(),
                performance.getPublishedAt(), false, performance.getCreatedAt(), performance.getUpdatedAt());
    }

    private PerformanceResponse toResponse(FestivalPerformance performance, boolean published) {
        List<MediaResponse> images = media(performance, PerformanceMediaKind.IMAGE);
        List<MediaResponse> videos = media(performance, PerformanceMediaKind.VIDEO);
        return new PerformanceResponse(performance.getId(), performance.getCategory(),
                performance.getCategory().getLabel(), performance.getTeamName(), performance.getMemberNames(),
                performance.getStartsAt(), performance.getEndsAt(), performance.getDescription(),
                performance.getLinks(), images, videos, performance.getPublishedAt(),
                published,
                performance.getCreatedAt(), performance.getUpdatedAt());
    }

    private List<MediaResponse> media(FestivalPerformance performance, PerformanceMediaKind kind) {
        return performance.getMedia().stream().filter(item -> item.getKind() == kind)
                .map(item -> new MediaResponse(item.getId(), item.getKind(), item.getSource(), item.getUrl()))
                .toList();
    }

    private <T> AuthorizedResult<T> rotated(AuthorizedResult<MeResponse> authenticated, T body) {
        return new AuthorizedResult<>(body, authenticated.newAccessToken(), authenticated.newRefreshToken());
    }

    private record Normalized(PerformanceCategory category, String teamName, List<String> memberNames,
                              Instant startsAt, Instant endsAt, String description, List<String> links,
                              List<String> imageUrls, List<String> videoUrls, Instant publishedAt) { }
}
