package org.syu_likelion.Festa_2026.booth;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.syu_likelion.Festa_2026.auth.AuthorizedSsoExecutor.AuthorizedResult;
import org.syu_likelion.Festa_2026.booth.BoothDtos.BoothAdminResponse;
import org.syu_likelion.Festa_2026.booth.BoothDtos.BoothDetailResponse;
import org.syu_likelion.Festa_2026.booth.BoothDtos.BoothMediaOrderRequest;
import org.syu_likelion.Festa_2026.booth.BoothDtos.BoothMediaResponse;
import org.syu_likelion.Festa_2026.booth.BoothDtos.BoothMutationRequest;
import org.syu_likelion.Festa_2026.booth.BoothDtos.BoothSummaryResponse;
import org.syu_likelion.Festa_2026.booth.BoothMediaStorage.StoredFile;
import org.syu_likelion.Festa_2026.error.ApiException;
import org.syu_likelion.Festa_2026.storage.TransactionalFileActions;
import org.syu_likelion.Festa_2026.user.FestivalRole;
import org.syu_likelion.Festa_2026.user.FestivalUser;
import org.syu_likelion.Festa_2026.user.FestivalUserRepository;
import org.syu_likelion.Festa_2026.user.UserDtos.MeResponse;
import org.syu_likelion.Festa_2026.user.UserService;

@Service
public class BoothService {
    private static final int MAX_IMAGES = 5;
    private static final int MAX_VIDEOS = 3;
    private final FestivalBoothRepository booths;
    private final BoothFavoriteRepository favorites;
    private final FestivalUserRepository festivalUsers;
    private final UserService users;
    private final BoothMediaStorage storage;

    public BoothService(FestivalBoothRepository booths, BoothFavoriteRepository favorites,
                        FestivalUserRepository festivalUsers, UserService users, BoothMediaStorage storage) {
        this.booths = booths; this.favorites = favorites; this.festivalUsers = festivalUsers;
        this.users = users; this.storage = storage;
    }

    @Transactional(readOnly = true)
    public AuthorizedResult<List<BoothSummaryResponse>> list(String authorizationToken, String refreshToken) {
        AuthorizedResult<MeResponse> auth = optionalAuth(authorizationToken, refreshToken);
        Set<Long> liked = auth == null ? Set.of() : new HashSet<>(favorites.findBoothIdsByUserUuid(auth.body().userUuid()));
        List<BoothSummaryResponse> body = booths.findAllByOrderByNameAsc().stream()
                .map(booth -> summary(booth, auth != null && liked.contains(booth.getId()))).toList();
        return rotated(auth, body);
    }

    @Transactional(readOnly = true)
    public AuthorizedResult<BoothDetailResponse> detail(Long id, String authorizationToken, String refreshToken) {
        AuthorizedResult<MeResponse> auth = optionalAuth(authorizationToken, refreshToken);
        FestivalBooth booth = find(id);
        boolean liked = auth != null && favorites.existsByBoothIdAndUserUserUuid(id, auth.body().userUuid());
        return rotated(auth, detail(booth, liked));
    }

    @Transactional(readOnly = true)
    public AuthorizedResult<List<BoothSummaryResponse>> myFavorites(String access, String refresh) {
        AuthorizedResult<MeResponse> auth = users.getMe(access, refresh);
        List<BoothSummaryResponse> body = favorites.findBoothsByUserUuid(auth.body().userUuid()).stream()
                .map(booth -> summary(booth, true)).toList();
        return rotated(auth, body);
    }

    @Transactional
    public AuthorizedResult<Void> favorite(Long id, String access, String refresh) {
        AuthorizedResult<MeResponse> auth = users.getMe(access, refresh);
        FestivalBooth booth = find(id);
        FestivalUser user = festivalUsers.findByUserUuid(auth.body().userUuid()).orElseThrow();
        if (!favorites.existsByBoothIdAndUserUserUuid(id, user.getUserUuid())) {
            favorites.save(new BoothFavorite(booth, user));
        }
        return rotated(auth, null);
    }

    @Transactional
    public AuthorizedResult<Void> unfavorite(Long id, String access, String refresh) {
        AuthorizedResult<MeResponse> auth = users.getMe(access, refresh);
        find(id);
        favorites.findByBoothIdAndUserUserUuid(id, auth.body().userUuid()).ifPresent(favorites::delete);
        return rotated(auth, null);
    }

    @Transactional
    public AuthorizedResult<BoothAdminResponse> create(String access, String refresh, BoothMutationRequest request) {
        AuthorizedResult<MeResponse> auth = users.getMe(access, refresh);
        requireAdmin(auth.body());
        BoothAdminResponse body = createAs(auth.body().userUuid(), request, List.of(), List.of());
        return rotated(auth, body);
    }

    @Transactional
    public BoothAdminResponse createAs(UUID actor, BoothMutationRequest request,
                                       List<MultipartFile> images, List<MultipartFile> videos) {
        Normalized n = normalize(request);
        Set<FestivalUser> managers = managers(n.managerUuids());
        FestivalBooth booth = new FestivalBooth(n.latitude(), n.longitude(), n.name(), n.operator(),
                n.description(), n.opensAt(), n.closesAt(), n.stampEnabled(), managers, actor);
        managers.forEach(user -> user.addRole(FestivalRole.BOOTH_MANAGER));
        List<StoredFile> stored = new ArrayList<>();
        boolean rollbackCleanup = TransactionalFileActions.deleteOnRollback(() -> deleteStored(stored));
        try {
            addUploads(booth, BoothMediaKind.IMAGE, images, stored);
            addUploads(booth, BoothMediaKind.VIDEO, videos, stored);
            chooseDefaultRepresentative(booth);
            return admin(booths.saveAndFlush(booth));
        } catch (RuntimeException failure) {
            if (!rollbackCleanup) deleteStored(stored);
            throw failure;
        }
    }

    @Transactional
    public AuthorizedResult<BoothAdminResponse> update(Long id, String access, String refresh,
                                                       BoothMutationRequest request) {
        AuthorizedResult<MeResponse> auth = users.getMe(access, refresh);
        requireAdmin(auth.body());
        return rotated(auth, updateAs(id, auth.body().userUuid(), request));
    }

    @Transactional
    public BoothAdminResponse updateAs(Long id, UUID actor, BoothMutationRequest request) {
        FestivalBooth booth = find(id);
        Normalized n = normalize(request);
        Set<FestivalUser> oldManagers = booth.getManagers();
        Set<FestivalUser> newManagers = managers(n.managerUuids());
        newManagers.forEach(user -> user.addRole(FestivalRole.BOOTH_MANAGER));
        booth.update(n.latitude(), n.longitude(), n.name(), n.operator(), n.description(), n.opensAt(),
                n.closesAt(), n.stampEnabled(), newManagers, actor);
        BoothAdminResponse response = admin(booths.saveAndFlush(booth));
        oldManagers.stream().filter(user -> !newManagers.contains(user)).forEach(this::removeManagerRoleIfUnused);
        return response;
    }

    @Transactional
    public AuthorizedResult<BoothAdminResponse> upload(Long id, BoothMediaKind kind, List<MultipartFile> files,
                                                       String access, String refresh) {
        AuthorizedResult<MeResponse> auth = users.getMe(access, refresh);
        requireAdmin(auth.body());
        return rotated(auth, uploadAs(id, kind, files));
    }

    @Transactional
    public BoothAdminResponse uploadAs(Long id, BoothMediaKind kind, List<MultipartFile> files) {
        FestivalBooth booth = findForUpdate(id);
        List<StoredFile> stored = new ArrayList<>();
        boolean rollbackCleanup = TransactionalFileActions.deleteOnRollback(() -> deleteStored(stored));
        try {
            addUploads(booth, kind, files, stored);
            chooseDefaultRepresentative(booth);
            return admin(booths.saveAndFlush(booth));
        } catch (RuntimeException failure) {
            if (!rollbackCleanup) deleteStored(stored);
            throw failure;
        }
    }

    @Transactional
    public AuthorizedResult<BoothAdminResponse> orderMedia(Long id, BoothMediaOrderRequest request,
                                                           String access, String refresh) {
        AuthorizedResult<MeResponse> auth = users.getMe(access, refresh);
        requireAdmin(auth.body());
        return rotated(auth, orderMediaAs(id, request));
    }

    @Transactional
    public BoothAdminResponse orderMediaAs(Long id, BoothMediaOrderRequest request) {
        FestivalBooth booth = findForUpdate(id);
        if (request == null || request.mediaIds() == null || request.representativeMediaId() == null)
            throw invalidMediaOrder();
        List<Long> ids = request.mediaIds();
        Map<Long, BoothMedia> existing = booth.getMedia().stream()
                .collect(Collectors.toMap(BoothMedia::getId, Function.identity()));
        if (ids.size() != existing.size() || new HashSet<>(ids).size() != ids.size()
                || !existing.keySet().equals(new HashSet<>(ids)) || !ids.contains(request.representativeMediaId()))
            throw invalidMediaOrder();
        List<BoothMedia> ordered = ids.stream().map(existing::get).toList();
        ordered.forEach(media -> media.setRepresentative(media.getId().equals(request.representativeMediaId())));
        booth.reorderMedia(ordered);
        return admin(booths.saveAndFlush(booth));
    }

    @Transactional
    public AuthorizedResult<BoothAdminResponse> deleteMedia(Long id, Long mediaId, String access, String refresh) {
        AuthorizedResult<MeResponse> auth = users.getMe(access, refresh);
        requireAdmin(auth.body());
        return rotated(auth, deleteMediaAs(id, mediaId));
    }

    @Transactional
    public BoothAdminResponse deleteMediaAs(Long id, Long mediaId) {
        FestivalBooth booth = findForUpdate(id);
        BoothMedia media = booth.getMedia().stream().filter(item -> item.getId().equals(mediaId)).findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "BOOTH_MEDIA_NOT_FOUND", "부스 미디어를 찾을 수 없습니다."));
        booth.removeMedia(media);
        chooseDefaultRepresentative(booth);
        BoothAdminResponse response = admin(booths.saveAndFlush(booth));
        TransactionalFileActions.deleteAfterCommit(() -> storage.delete(media.getStorageKey()));
        return response;
    }

    @Transactional
    public AuthorizedResult<Void> delete(Long id, String access, String refresh) {
        AuthorizedResult<MeResponse> auth = users.getMe(access, refresh);
        requireAdmin(auth.body());
        deleteAs(id);
        return rotated(auth, null);
    }

    @Transactional
    public void deleteAs(Long id) {
        FestivalBooth booth = findForUpdate(id);
        Set<FestivalUser> managers = booth.getManagers();
        List<String> keys = booth.getMedia().stream().map(BoothMedia::getStorageKey).toList();
        favorites.deleteAllByBoothId(id);
        booths.delete(booth); booths.flush();
        managers.forEach(this::removeManagerRoleIfUnused);
        TransactionalFileActions.deleteAfterCommit(() -> keys.forEach(storage::delete));
    }

    @Transactional(readOnly = true) public List<BoothAdminResponse> listAdmin() {
        return booths.findAllByOrderByNameAsc().stream().map(this::admin).toList();
    }
    @Transactional(readOnly = true) public BoothAdminResponse getAdmin(Long id) { return admin(find(id)); }

    private AuthorizedResult<MeResponse> optionalAuth(String access, String refresh) {
        return access == null || access.isBlank() ? null : users.getMe(access, refresh);
    }
    private void requireAdmin(MeResponse me) {
        Set<FestivalRole> roles = me.festivalRoles();
        if (roles == null || (!roles.contains(FestivalRole.ADMIN) && !roles.contains(FestivalRole.SUPER_ADMIN)))
            throw new ApiException(HttpStatus.FORBIDDEN, "BOOTH_MANAGE_FORBIDDEN", "부스 정보는 ADMIN 이상만 관리할 수 있습니다.");
    }
    private FestivalBooth find(Long id) { return booths.findById(id).orElseThrow(() ->
            new ApiException(HttpStatus.NOT_FOUND, "BOOTH_NOT_FOUND", "부스 정보를 찾을 수 없습니다.")); }
    private FestivalBooth findForUpdate(Long id) { return booths.findByIdForUpdate(id).orElseThrow(() ->
            new ApiException(HttpStatus.NOT_FOUND, "BOOTH_NOT_FOUND", "부스 정보를 찾을 수 없습니다.")); }
    private ApiException invalidMediaOrder() {
        return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_BOOTH_MEDIA_ORDER",
                "미디어 순서 또는 대표 미디어를 확인해 주세요.");
    }

    private Normalized normalize(BoothMutationRequest r) {
        if (r == null || r.latitude() == null || r.longitude() == null || r.opensAt() == null || r.closesAt() == null)
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_BOOTH", "필수 부스 정보를 입력해 주세요.");
        if (r.latitude().compareTo(java.math.BigDecimal.valueOf(-90)) < 0 || r.latitude().compareTo(java.math.BigDecimal.valueOf(90)) > 0
                || r.longitude().compareTo(java.math.BigDecimal.valueOf(-180)) < 0 || r.longitude().compareTo(java.math.BigDecimal.valueOf(180)) > 0)
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_BOOTH_COORDINATES", "위도 또는 경도를 확인해 주세요.");
        if (!r.closesAt().isAfter(r.opensAt()))
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_BOOTH_HOURS", "운영 종료시간은 시작시간보다 늦어야 합니다.");
        return new Normalized(r.latitude(), r.longitude(), text(r.name(), 150, "부스 이름"),
                text(r.operator(), 150, "운영 주체"), text(r.description(), 5000, "부스 설명"),
                r.opensAt(), r.closesAt(), r.stampEnabled(),
                r.managerUuids() == null ? List.of() : r.managerUuids().stream().distinct().toList());
    }
    private String text(String value, int max, String field) {
        String cleaned = value == null ? "" : value.trim();
        if (cleaned.isBlank() || cleaned.length() > max)
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_BOOTH", field + "을(를) 확인해 주세요.");
        return cleaned;
    }
    private Set<FestivalUser> managers(List<UUID> uuids) {
        if (uuids.isEmpty()) return Set.of();
        List<FestivalUser> found = festivalUsers.findAllByUserUuidIn(uuids);
        if (found.size() != uuids.size()) throw new ApiException(HttpStatus.BAD_REQUEST, "BOOTH_MANAGER_NOT_FOUND",
                "축제 서비스에 연결되지 않은 부스 관리자가 포함되어 있습니다.");
        return new LinkedHashSet<>(found);
    }
    private void removeManagerRoleIfUnused(FestivalUser user) {
        if (booths.countByManagersContains(user) == 0) user.removeRole(FestivalRole.BOOTH_MANAGER);
    }
    private void addUploads(FestivalBooth booth, BoothMediaKind kind, List<MultipartFile> files, List<StoredFile> stored) {
        List<MultipartFile> uploads = files == null ? List.of() : files.stream().filter(f -> f != null && !f.isEmpty()).toList();
        long current = booth.getMedia().stream().filter(item -> item.getKind() == kind).count();
        int max = kind == BoothMediaKind.IMAGE ? MAX_IMAGES : MAX_VIDEOS;
        if (current + uploads.size() > max) throw new ApiException(HttpStatus.BAD_REQUEST, "TOO_MANY_BOOTH_MEDIA",
                kind == BoothMediaKind.IMAGE ? "부스 이미지는 최대 5개입니다." : "부스 동영상은 최대 3개입니다.");
        for (MultipartFile file : uploads) {
            StoredFile saved = storage.store(file, kind); stored.add(saved);
            booth.addMedia(new BoothMedia(kind, saved.url(), saved.storageKey(), saved.originalFilename()));
        }
    }
    private void chooseDefaultRepresentative(FestivalBooth booth) {
        List<BoothMedia> media = booth.getMedia();
        if (media.isEmpty()) return;
        if (media.stream().noneMatch(BoothMedia::isRepresentative)) media.getFirst().setRepresentative(true);
    }
    private void deleteStored(List<StoredFile> stored) {
        stored.forEach(file -> storage.delete(file.storageKey()));
    }
    private BoothSummaryResponse summary(FestivalBooth booth, boolean liked) {
        return new BoothSummaryResponse(booth.getId(), booth.getLatitude(), booth.getLongitude(), booth.getName(),
                booth.getOperator(), booth.getOpensAt(), booth.getClosesAt(), booth.isStampEnabled(),
                representative(booth), liked);
    }
    private BoothDetailResponse detail(FestivalBooth booth, boolean liked) {
        List<BoothMediaResponse> media = media(booth);
        return new BoothDetailResponse(booth.getId(), booth.getLatitude(), booth.getLongitude(), booth.getName(),
                booth.getOperator(), booth.getDescription(), booth.getOpensAt(), booth.getClosesAt(),
                booth.isStampEnabled(), media,
                media.stream().filter(BoothMediaResponse::representative).findFirst().orElse(null), liked,
                booth.getCreatedAt(), booth.getUpdatedAt());
    }
    private BoothAdminResponse admin(FestivalBooth booth) {
        return new BoothAdminResponse(detail(booth, false), booth.getManagers().stream().map(FestivalUser::getUserUuid).toList());
    }
    private List<BoothMediaResponse> media(FestivalBooth booth) {
        List<BoothMedia> items = booth.getMedia();
        List<BoothMediaResponse> result = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            BoothMedia item = items.get(i);
            result.add(new BoothMediaResponse(item.getId(), item.getKind(), item.getUrl(), i, item.isRepresentative()));
        }
        return List.copyOf(result);
    }
    private BoothMediaResponse representative(FestivalBooth booth) {
        return media(booth).stream().filter(BoothMediaResponse::representative).findFirst().orElse(null);
    }
    private <T> AuthorizedResult<T> rotated(AuthorizedResult<MeResponse> auth, T body) {
        return auth == null ? new AuthorizedResult<>(body, null, null)
                : new AuthorizedResult<>(body, auth.newAccessToken(), auth.newRefreshToken());
    }
    private record Normalized(java.math.BigDecimal latitude, java.math.BigDecimal longitude, String name,
                              String operator, String description, java.time.LocalTime opensAt,
                              java.time.LocalTime closesAt, boolean stampEnabled, List<UUID> managerUuids) { }
}
