package org.syu_likelion.Festa_2026.stamp;

import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.syu_likelion.Festa_2026.error.ApiException;
import org.syu_likelion.Festa_2026.qr.QrService;
import org.syu_likelion.Festa_2026.sso.SsoInternalProfileClient;
import org.syu_likelion.Festa_2026.sso.SsoProfiles.InternalUserProfile;
import org.syu_likelion.Festa_2026.stamp.StampDtos.StampItemResponse;
import org.syu_likelion.Festa_2026.stamp.StampDtos.StampPrizeTargetResponse;
import org.syu_likelion.Festa_2026.user.FestivalRole;
import org.syu_likelion.Festa_2026.user.FestivalUserRepository;

@Service
public class StampPrizeService {
    public static final int REQUIRED_STAMPS = StampBoardPolicy.MAX_STAMPS;
    private final StampPrizeRepository prizes;
    private final BoothStampRepository stamps;
    private final FestivalUserRepository users;
    private final QrService qr;
    private final SsoInternalProfileClient profiles;
    private final Clock clock;
    private final StampPrizeEventRepository events;

    public StampPrizeService(StampPrizeRepository prizes, BoothStampRepository stamps,
                             FestivalUserRepository users, QrService qr,
                             SsoInternalProfileClient profiles, Clock clock, StampPrizeEventRepository events) {
        this.prizes = prizes; this.stamps = stamps; this.users = users;
        this.qr = qr; this.profiles = profiles; this.clock = clock; this.events = events;
    }

    @Transactional(readOnly = true)
    public StampPrizeTargetResponse lookupQrAs(FestivalRole role, String token) {
        requireAdmin(role);
        return lookupUserAs(role, qr.resolveUserUuid(token));
    }

    @Transactional(readOnly = true)
    public StampPrizeTargetResponse lookupUserAs(FestivalRole role, UUID target) {
        requireAdmin(role);
        if (users.findByUserUuid(target).isEmpty()) throw unlinked();
        InternalUserProfile profile = profiles.getProfile(target);
        List<BoothStamp> board = stamps.findAllByUserUserUuidOrderByGrantedAtAsc(target);
        return response(target, profile, board, prizes.findByTargetUserUuid(target).orElse(null));
    }

    @Transactional
    public StampPrizeTargetResponse grantQrAs(UUID actor, FestivalRole role, String token) {
        requireAdmin(role);
        return grant(actor, qr.resolveUserUuid(token), StampMethod.QR);
    }

    @Transactional
    public StampPrizeTargetResponse grantUserAs(UUID actor, FestivalRole role, UUID target) {
        requireAdmin(role);
        return grant(actor, target, StampMethod.ADMIN_SEARCH);
    }

    private StampPrizeTargetResponse grant(UUID actor, UUID target, StampMethod method) {
        InternalUserProfile profile = profiles.getProfile(target);
        // Serialize redemption and stamp mutations for the same user.
        users.findByUserUuidForUpdate(target).orElseThrow(StampPrizeService::unlinked);
        StampPrize existing = prizes.findByTargetUserUuidForUpdate(target).orElse(null);
        if (existing != null && existing.isIssued()) {
            throw new ApiException(HttpStatus.CONFLICT, "STAMP_PRIZE_ALREADY_GRANTED", "이미 상품을 지급한 사용자입니다.");
        }
        List<BoothStamp> board = stamps.findAllForUserForUpdate(target);
        if (!StampBoardPolicy.complete(board)) {
            throw new ApiException(HttpStatus.CONFLICT, "STAMP_PRIZE_NOT_READY", "스탬프 6개를 모아야 상품을 지급할 수 있습니다.");
        }
        StampPrize prize;
        if (existing == null) prize = prizes.saveAndFlush(new StampPrize(target, actor, clock.instant(), board.size()));
        else {
            existing.reissue(actor, clock.instant(), board.size());
            prize = prizes.saveAndFlush(existing);
        }
        events.save(new StampPrizeEvent(prize, StampAction.GRANT, method, actor, prize.getGrantedAt(), null));
        return response(target, profile, board, prize);
    }

    public org.syu_likelion.Festa_2026.qr.QrDtos.UserSearchResponse searchAs(FestivalRole role, String query, int page) {
        requireAdmin(role);
        return qr.searchAs(role, query, Math.max(0, page), 20);
    }

    public record RecordView(Long id, UUID userUuid, String name, String studentNo, String department,
                             boolean issued, UUID grantedBy, String grantedByName, java.time.Instant grantedAt,
                             int stampCount, long version) { }
    public record EventView(Long id, StampAction action, StampMethod method, UUID actorUuid, String actorName,
                            java.time.Instant occurredAt, int stampCount, String reason) { }

    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<RecordView> listAs(FestivalRole role, int page) {
        requireAdmin(role);
        var records = prizes.findAll(org.springframework.data.domain.PageRequest.of(Math.max(0, page), 20,
                org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Order.desc("grantedAt"),
                        org.springframework.data.domain.Sort.Order.desc("id"))));
        var ids = new java.util.HashSet<UUID>();
        records.forEach(p -> { ids.add(p.getTargetUserUuid()); ids.add(p.getGrantedBy()); });
        var map = profileMap(ids);
        return records.map(p -> record(p, map));
    }

    @Transactional(readOnly = true)
    public long issuedCountAs(FestivalRole role) { requireAdmin(role); return prizes.countByIssuedTrue(); }

    @Transactional(readOnly = true)
    public RecordView detailAs(FestivalRole role, Long id) {
        requireAdmin(role);
        StampPrize prize = find(id);
        return record(prize, profileMap(new java.util.HashSet<>(List.of(prize.getTargetUserUuid(), prize.getGrantedBy()))));
    }

    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<EventView> historyAs(FestivalRole role, Long id, int page) {
        requireAdmin(role);
        StampPrize prize = find(id);
        var pageable = org.springframework.data.domain.PageRequest.of(Math.max(0, page), 20,
                org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Order.desc("occurredAt"),
                        org.springframework.data.domain.Sort.Order.desc("id")));
        var result = events.findByPrizeId(id, pageable);
        var ids = new java.util.HashSet<UUID>();
        result.forEach(e -> ids.add(e.getActorUuid()));
        ids.add(prize.getGrantedBy());
        var map = profileMap(ids);
        // Show the original grant for records created before event history was introduced.
        if (result.getTotalElements() == 0) {
            var initial = new EventView(null, StampAction.GRANT, StampMethod.QR, prize.getGrantedBy(),
                    displayName(map.get(prize.getGrantedBy()), prize.getGrantedBy()), prize.getGrantedAt(), prize.getStampCount(), "기존 지급 기록");
            return new org.springframework.data.domain.PageImpl<>(page == 0 ? List.of(initial) : List.of(), pageable, 1);
        }
        return result.map(e -> new EventView(e.getId(), e.getAction(), e.getMethod(), e.getActorUuid(),
                displayName(map.get(e.getActorUuid()), e.getActorUuid()), e.getOccurredAt(), e.getStampCount(), e.getReason()));
    }

    @Transactional
    public void revokeAs(UUID actor, FestivalRole role, Long id, long version, String reason) {
        requireAdmin(role);
        String cleaned = reason == null ? "" : reason.trim();
        if (cleaned.isEmpty() || cleaned.length() > 500) throw new ApiException(HttpStatus.BAD_REQUEST,
                "INVALID_STAMP_PRIZE_REASON", "철회 사유를 1~500자로 입력해 주세요.");
        StampPrize initial = find(id);
        // Same lock order as issuance. Withdrawn users can still have their retained record corrected.
        users.findByUserUuidForUpdate(initial.getTargetUserUuid());
        StampPrize prize = prizes.findByIdForUpdate(id).orElseThrow(() -> notFound());
        // Refresh after waiting for locks: the record may already be in the persistence context.
        entityRefresh(prize);
        if (prize.getVersion() != version) throw new ApiException(HttpStatus.CONFLICT,
                "STAMP_PRIZE_CHANGED", "지급 상태가 변경되었습니다. 새로 조회해 주세요.");
        if (!prize.isIssued()) throw new ApiException(HttpStatus.CONFLICT,
                "STAMP_PRIZE_ALREADY_REVOKED", "이미 철회한 지급 기록입니다.");
        if (!events.existsByPrizeId(id)) events.save(new StampPrizeEvent(prize, StampAction.GRANT,
                StampMethod.QR, prize.getGrantedBy(), prize.getGrantedAt(), "기존 지급 기록"));
        prize.revoke();
        prizes.saveAndFlush(prize);
        events.save(new StampPrizeEvent(prize, StampAction.REVOKE, StampMethod.ADMIN_SEARCH, actor, clock.instant(), cleaned));
    }

    @jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager entityManager;
    private void entityRefresh(StampPrize prize) { entityManager.refresh(prize, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE); }
    private StampPrize find(Long id) { return prizes.findById(id).orElseThrow(() -> notFound()); }
    private ApiException notFound() { return new ApiException(HttpStatus.NOT_FOUND, "STAMP_PRIZE_NOT_FOUND", "상품 지급 기록을 찾을 수 없습니다."); }
    private java.util.Map<UUID, InternalUserProfile> profileMap(java.util.Set<UUID> ids) {
        var result = new java.util.HashMap<UUID, InternalUserProfile>();
        if (ids.isEmpty()) return result;
        try { profiles.getProfiles(List.copyOf(ids)).forEach(p -> result.put(p.userUuid(), p)); }
        catch (org.syu_likelion.Festa_2026.sso.SsoException unavailable) { /* Keep audit records readable with UUID fallback. */ }
        return result;
    }
    private String displayName(InternalUserProfile profile, UUID uuid) {
        return profile == null || profile.name() == null ? uuid.toString() : profile.name();
    }
    private RecordView record(StampPrize p, java.util.Map<UUID, InternalUserProfile> map) {
        var user = map.get(p.getTargetUserUuid());
        return new RecordView(p.getId(), p.getTargetUserUuid(), displayName(user, p.getTargetUserUuid()),
                user == null ? null : user.studentNo(), user == null ? null : user.department(), p.isIssued(),
                p.getGrantedBy(), displayName(map.get(p.getGrantedBy()), p.getGrantedBy()), p.getGrantedAt(), p.getStampCount(), p.getVersion());
    }

    private StampPrizeTargetResponse response(UUID target, InternalUserProfile profile,
                                               List<BoothStamp> board, StampPrize prize) {
        List<StampItemResponse> items = board.stream().map(stamp -> new StampItemResponse(
                stamp.getBooth().getId(), stamp.getBooth().getName(), stamp.getBooth().getOperator(), stamp.getGrantedAt(), stamp.getBooth().getCategory())).toList();
        return new StampPrizeTargetResponse(target, profile.name(), profile.studentNo(), profile.department(),
                items.size(), REQUIRED_STAMPS, items, (prize == null || !prize.isIssued()) && StampBoardPolicy.complete(board),
                prize != null && prize.isIssued(), prize == null || !prize.isIssued() ? null : prize.getGrantedAt(), prize == null || !prize.isIssued() ? null : prize.getGrantedBy());
    }

    private void requireAdmin(FestivalRole role) {
        if (role != FestivalRole.ADMIN && role != FestivalRole.SUPER_ADMIN) {
            throw new ApiException(HttpStatus.FORBIDDEN, "STAMP_PRIZE_FORBIDDEN", "상품 지급은 ADMIN 이상만 처리할 수 있습니다.");
        }
    }
    private static ApiException unlinked() {
        return new ApiException(HttpStatus.BAD_REQUEST, "STAMP_USER_NOT_LINKED", "축제 서비스에 연결되지 않은 사용자입니다.");
    }
}
