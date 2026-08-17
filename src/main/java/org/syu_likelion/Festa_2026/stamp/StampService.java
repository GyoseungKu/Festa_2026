package org.syu_likelion.Festa_2026.stamp;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.syu_likelion.Festa_2026.auth.AuthorizedSsoExecutor.AuthorizedResult;
import org.syu_likelion.Festa_2026.booth.FestivalBooth;
import org.syu_likelion.Festa_2026.booth.FestivalBoothRepository;
import org.syu_likelion.Festa_2026.error.ApiException;
import org.syu_likelion.Festa_2026.qr.QrService;
import org.syu_likelion.Festa_2026.sso.SsoInternalProfileClient;
import org.syu_likelion.Festa_2026.sso.SsoProfiles.InternalUserProfile;
import org.syu_likelion.Festa_2026.stamp.StampDtos.BoothStampAdminResponse;
import org.syu_likelion.Festa_2026.stamp.StampDtos.CurrentStampResponse;
import org.syu_likelion.Festa_2026.stamp.StampDtos.MyStampBoardResponse;
import org.syu_likelion.Festa_2026.stamp.StampDtos.StampHistoryResponse;
import org.syu_likelion.Festa_2026.stamp.StampDtos.StampItemResponse;
import org.syu_likelion.Festa_2026.stamp.StampDtos.StampTargetResponse;
import org.syu_likelion.Festa_2026.user.FestivalRole;
import org.syu_likelion.Festa_2026.user.FestivalUser;
import org.syu_likelion.Festa_2026.user.FestivalUserRepository;
import org.syu_likelion.Festa_2026.user.UserDtos.MeResponse;
import org.syu_likelion.Festa_2026.user.UserService;

@Service
public class StampService {
    private final BoothStampRepository stamps;
    private final StampEventRepository events;
    private final FestivalBoothRepository booths;
    private final FestivalUserRepository festivalUsers;
    private final UserService users;
    private final QrService qr;
    private final SsoInternalProfileClient profiles;
    private final Clock clock;

    public StampService(BoothStampRepository stamps, StampEventRepository events,
                        FestivalBoothRepository booths, FestivalUserRepository festivalUsers,
                        UserService users, QrService qr, SsoInternalProfileClient profiles, Clock clock) {
        this.stamps = stamps; this.events = events; this.booths = booths; this.festivalUsers = festivalUsers;
        this.users = users; this.qr = qr; this.profiles = profiles; this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AuthorizedResult<StampTargetResponse> lookupQr(Long boothId, String token, String access, String refresh) {
        AuthorizedResult<MeResponse> auth = users.getMe(access, refresh);
        FestivalRole role = stampRole(auth.body().festivalRoles());
        return rotated(auth, lookupQrAs(auth.body().userUuid(), role, boothId, token));
    }

    @Transactional
    public AuthorizedResult<StampTargetResponse> grantQr(Long boothId, String token, String access, String refresh) {
        AuthorizedResult<MeResponse> auth = users.getMe(access, refresh);
        FestivalRole role = stampRole(auth.body().festivalRoles());
        return rotated(auth, grantQrAs(auth.body().userUuid(), role, boothId, token));
    }

    @Transactional
    public AuthorizedResult<StampTargetResponse> revokeQr(Long boothId, String token, String access, String refresh) {
        AuthorizedResult<MeResponse> auth = users.getMe(access, refresh);
        FestivalRole role = stampRole(auth.body().festivalRoles());
        return rotated(auth, revokeQrAs(auth.body().userUuid(), role, boothId, token));
    }

    @Transactional
    public AuthorizedResult<StampTargetResponse> grantBySearch(Long boothId, UUID target, String access, String refresh) {
        AuthorizedResult<MeResponse> auth = users.getMe(access, refresh);
        requireAdmin(auth.body().festivalRoles());
        return rotated(auth, grantBySearchAs(auth.body().userUuid(), boothId, target));
    }

    @Transactional
    public AuthorizedResult<StampTargetResponse> revokeBySearch(Long boothId, UUID target, String access, String refresh) {
        AuthorizedResult<MeResponse> auth = users.getMe(access, refresh);
        requireAdmin(auth.body().festivalRoles());
        return rotated(auth, revokeBySearchAs(auth.body().userUuid(), boothId, target));
    }

    @Transactional(readOnly = true)
    public AuthorizedResult<BoothStampAdminResponse> adminHistory(Long boothId, String access, String refresh) {
        AuthorizedResult<MeResponse> auth = users.getMe(access, refresh);
        FestivalRole role = stampRole(auth.body().festivalRoles());
        return rotated(auth, historyAs(auth.body().userUuid(), role, boothId));
    }

    @Transactional(readOnly = true)
    public AuthorizedResult<MyStampBoardResponse> myBoard(String access, String refresh) {
        AuthorizedResult<MeResponse> auth = users.getMe(access, refresh);
        UUID userUuid = auth.body().userUuid();
        List<StampItemResponse> items = stamps.findAllByUserUserUuidOrderByGrantedAtAsc(userUuid).stream()
                .map(stamp -> new StampItemResponse(stamp.getBooth().getId(), stamp.getBooth().getName(),
                        stamp.getBooth().getOperator(), stamp.getGrantedAt())).toList();
        boolean participated = !items.isEmpty() || events.existsByTargetUserUuidAndAction(userUuid, StampAction.GRANT);
        return rotated(auth, new MyStampBoardResponse(participated, items.size(), items));
    }

    @Transactional(readOnly = true)
    public StampTargetResponse lookupQrAs(UUID actor, FestivalRole role, Long boothId, String token) {
        FestivalBooth booth = accessibleBooth(actor, role, boothId);
        UUID target = qr.resolveUserUuid(token);
        return target(booth, target, role);
    }

    @Transactional
    public StampTargetResponse grantQrAs(UUID actor, FestivalRole role, Long boothId, String token) {
        FestivalBooth booth = accessibleBooth(actor, role, boothId);
        UUID target = qr.resolveUserUuid(token);
        grant(booth, target, actor, StampMethod.QR);
        return target(booth, target, role);
    }

    @Transactional
    public StampTargetResponse revokeQrAs(UUID actor, FestivalRole role, Long boothId, String token) {
        FestivalBooth booth = accessibleBooth(actor, role, boothId);
        UUID target = qr.resolveUserUuid(token);
        revoke(booth, target, actor, StampMethod.QR);
        return target(booth, target, role);
    }

    @Transactional
    public StampTargetResponse grantBySearchAs(UUID actor, Long boothId, UUID target) {
        FestivalBooth booth = findBooth(boothId);
        grant(booth, target, actor, StampMethod.ADMIN_SEARCH);
        return target(booth, target, FestivalRole.ADMIN);
    }

    @Transactional
    public StampTargetResponse revokeBySearchAs(UUID actor, Long boothId, UUID target) {
        FestivalBooth booth = findBooth(boothId);
        revoke(booth, target, actor, StampMethod.ADMIN_SEARCH);
        return target(booth, target, FestivalRole.ADMIN);
    }

    @Transactional(readOnly = true)
    public List<FestivalBooth> availableBoothsAs(UUID actor, FestivalRole role) {
        if (isAdmin(role)) return booths.findAllByStampEnabledTrueOrderByNameAsc();
        if (role == FestivalRole.BOOTH_MANAGER)
            return booths.findAllByStampEnabledTrueAndManagersUserUuidOrderByNameAsc(actor);
        throw forbidden();
    }

    @Transactional(readOnly = true)
    public BoothStampAdminResponse historyAs(UUID actor, FestivalRole role, Long boothId) {
        FestivalBooth booth = accessibleBooth(actor, role, boothId);
        boolean fullProfile = isAdmin(role);
        List<BoothStamp> current = stamps.findAllByBoothIdOrderByGrantedAtDesc(boothId);
        List<StampEvent> history = events.findAllByBoothIdOrderByOccurredAtDesc(boothId);
        Set<UUID> ids = new LinkedHashSet<>();
        current.forEach(stamp -> ids.add(stamp.getUser().getUserUuid()));
        history.forEach(event -> { ids.add(event.getTargetUserUuid()); ids.add(event.getActorUuid()); });
        Map<UUID, InternalUserProfile> profileMap = profileMap(ids);
        List<CurrentStampResponse> currentResponses = current.stream().map(stamp -> {
            UUID id = stamp.getUser().getUserUuid();
            InternalUserProfile profile = profileMap.get(id);
            return new CurrentStampResponse(fullProfile ? id : null, visibleName(profile, id, fullProfile),
                    visibleStudentNo(profile, fullProfile), stamp.getGrantedAt(),
                    fullProfile ? stamp.getGrantedBy() : null, stamp.getGrantMethod());
        }).toList();
        List<StampHistoryResponse> historyResponses = history.stream().map(event ->
                new StampHistoryResponse(event.getId(), event.getAction(), event.getMethod(),
                        fullProfile ? event.getTargetUserUuid() : null,
                        visibleName(profileMap.get(event.getTargetUserUuid()), event.getTargetUserUuid(), fullProfile),
                        fullProfile ? event.getActorUuid() : null,
                        visibleName(profileMap.get(event.getActorUuid()), event.getActorUuid(), fullProfile),
                        event.getOccurredAt())).toList();
        return new BoothStampAdminResponse(boothId, booth.getName(), currentResponses, historyResponses);
    }

    private void grant(FestivalBooth booth, UUID targetUuid, UUID actor, StampMethod method) {
        if (stamps.existsByBoothIdAndUserUserUuid(booth.getId(), targetUuid))
            throw new ApiException(HttpStatus.CONFLICT, "STAMP_ALREADY_GRANTED", "이미 이 부스의 스탬프를 받은 사용자입니다.");
        FestivalUser target = festivalUsers.findByUserUuid(targetUuid).orElseThrow(() ->
                new ApiException(HttpStatus.BAD_REQUEST, "STAMP_USER_NOT_LINKED", "축제 서비스에 연결되지 않은 사용자입니다."));
        Instant now = clock.instant();
        stamps.saveAndFlush(new BoothStamp(booth, target, now, actor, method));
        events.save(new StampEvent(booth, targetUuid, actor, StampAction.GRANT, method, now));
    }

    private void revoke(FestivalBooth booth, UUID targetUuid, UUID actor, StampMethod method) {
        BoothStamp stamp = stamps.findByBoothIdAndUserUserUuid(booth.getId(), targetUuid).orElseThrow(() ->
                new ApiException(HttpStatus.CONFLICT, "STAMP_NOT_GRANTED", "이 부스에서 지급된 스탬프가 없습니다."));
        stamps.delete(stamp);
        events.save(new StampEvent(booth, targetUuid, actor, StampAction.REVOKE, method, clock.instant()));
    }

    private StampTargetResponse target(FestivalBooth booth, UUID targetUuid, FestivalRole viewerRole) {
        InternalUserProfile profile = profiles.getProfile(targetUuid);
        BoothStamp stamp = stamps.findByBoothIdAndUserUserUuid(booth.getId(), targetUuid).orElse(null);
        boolean admin = isAdmin(viewerRole);
        return new StampTargetResponse(admin ? targetUuid : null,
                admin ? profile.name() : QrService.maskName(profile.name()),
                admin ? profile.studentNo() : QrService.maskStudentNo(profile.studentNo()),
                profile.department(), stamp != null, stamp == null ? null : stamp.getGrantedAt());
    }

    private FestivalBooth accessibleBooth(UUID actor, FestivalRole role, Long boothId) {
        FestivalBooth booth = findBooth(boothId);
        if (!booth.isStampEnabled())
            throw new ApiException(HttpStatus.BAD_REQUEST, "STAMP_DISABLED_BOOTH", "스탬프를 지급하지 않는 부스입니다.");
        if (isAdmin(role)) return booth;
        if (role == FestivalRole.BOOTH_MANAGER && booths.existsByIdAndManagersUserUuid(boothId, actor)) return booth;
        throw forbidden();
    }
    private FestivalBooth findBooth(Long id) {
        return booths.findById(id).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "BOOTH_NOT_FOUND", "부스 정보를 찾을 수 없습니다."));
    }
    private FestivalRole stampRole(Set<FestivalRole> roles) {
        if (roles != null && roles.contains(FestivalRole.SUPER_ADMIN)) return FestivalRole.SUPER_ADMIN;
        if (roles != null && roles.contains(FestivalRole.ADMIN)) return FestivalRole.ADMIN;
        if (roles != null && roles.contains(FestivalRole.BOOTH_MANAGER)) return FestivalRole.BOOTH_MANAGER;
        throw forbidden();
    }
    private void requireAdmin(Set<FestivalRole> roles) {
        if (roles == null || (!roles.contains(FestivalRole.ADMIN) && !roles.contains(FestivalRole.SUPER_ADMIN))) throw forbidden();
    }
    private boolean isAdmin(FestivalRole role) { return role == FestivalRole.ADMIN || role == FestivalRole.SUPER_ADMIN; }
    private ApiException forbidden() {
        return new ApiException(HttpStatus.FORBIDDEN, "STAMP_MANAGE_FORBIDDEN", "스탬프 관리 권한이 없습니다.");
    }
    private Map<UUID, InternalUserProfile> profileMap(Set<UUID> ids) {
        Map<UUID, InternalUserProfile> result = new HashMap<>();
        List<UUID> all = new ArrayList<>(ids);
        for (int start = 0; start < all.size(); start += 100) {
            List<UUID> batch = all.subList(start, Math.min(start + 100, all.size()));
            try {
                profiles.getProfiles(batch).forEach(profile -> result.put(profile.userUuid(), profile));
            } catch (RuntimeException unavailable) {
                // 감사 이력은 UUID fallback으로 계속 표시합니다.
            }
        }
        return result;
    }
    private String name(InternalUserProfile profile, UUID fallback) {
        return profile == null || profile.name() == null || profile.name().isBlank() ? fallback.toString() : profile.name();
    }
    private String visibleName(InternalUserProfile profile, UUID fallback, boolean fullProfile) {
        if (fullProfile) return name(profile, fallback);
        if (profile == null || profile.name() == null || profile.name().isBlank()) return "확인 불가";
        return QrService.maskName(profile.name());
    }
    private String visibleStudentNo(InternalUserProfile profile, boolean fullProfile) {
        if (profile == null) return null;
        return fullProfile ? profile.studentNo() : QrService.maskStudentNo(profile.studentNo());
    }
    private <T> AuthorizedResult<T> rotated(AuthorizedResult<MeResponse> auth, T body) {
        return new AuthorizedResult<>(body, auth.newAccessToken(), auth.newRefreshToken());
    }
}
