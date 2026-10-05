package org.syu_likelion.Festa_2026.wristband;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.syu_likelion.Festa_2026.error.ApiException;
import org.syu_likelion.Festa_2026.fee.StudentFeeService;
import org.syu_likelion.Festa_2026.sso.SsoInternalProfileClient;
import org.syu_likelion.Festa_2026.user.*;

@Service
public class WristbandService {
    private final WristbandRepository records;
    private final WristbandEventRepository events;
    private final FestivalUserRepository users;
    private final StudentFeeService fees;
    private final SsoInternalProfileClient profiles;
    private final Clock clock;
    public WristbandService(WristbandRepository records, WristbandEventRepository events,
            FestivalUserRepository users, StudentFeeService fees, SsoInternalProfileClient profiles, Clock clock) {
        this.records = records; this.events = events; this.users = users;
        this.fees = fees; this.profiles = profiles; this.clock = clock;
    }
    public static void requireStaff(FestivalRole role) {
        if (role != FestivalRole.STAFF && !isAdmin(role))
            throw new ApiException(HttpStatus.FORBIDDEN, "WRISTBAND_FORBIDDEN", "팔찌 조회·지급은 STAFF 이상만 가능합니다.");
    }
    public static boolean isAdmin(FestivalRole role) {
        return role == FestivalRole.ADMIN || role == FestivalRole.SUPER_ADMIN;
    }
    public static void requireAdmin(FestivalRole role) {
        if (!isAdmin(role)) throw new ApiException(HttpStatus.FORBIDDEN, "WRISTBAND_MANAGE_FORBIDDEN", "지급 관리·철회는 ADMIN 이상만 가능합니다.");
    }
    public record MyStatus(
            @io.swagger.v3.oas.annotations.media.Schema(description = "현재 유효한 팔찌 지급 여부. 철회 후 false이며 과거 지급 이력의 존재 여부와 다릅니다.") boolean issued,
            @io.swagger.v3.oas.annotations.media.Schema(description = "현재 지급 시각. 미지급·철회 상태이면 null.", types = {"string", "null"}, format = "date-time") Instant issuedAt,
            @io.swagger.v3.oas.annotations.media.Schema(description = "현재 학생 인증 여부. 미인증도 본인 상태 조회는 가능하지만 지급은 불가합니다.") boolean schoolVerified) { }
    public record Eligibility(boolean schoolVerified, boolean studentFeePaid, boolean identityAvailable,
                              boolean issued, Instant issuedAt, Long wristbandId) {
        public boolean canIssue() { return schoolVerified && identityAvailable && !issued; }
    }
    @Transactional(readOnly = true)
    public MyStatus mine(UUID userUuid) {
        var status = status(userUuid);
        return new MyStatus(status.issued(), status.issuedAt(), status.schoolVerified());
    }

    /** 전체 회원 페이지에서 한 명마다 사용자·팔찌를 재조회하지 않는다. 재가입 해시 대조도 유지한다. */
    @Transactional(readOnly = true)
    public java.util.Map<UUID, MyStatus> forPage(java.util.List<FestivalUser> page) {
        if (page.size() > 100) throw new IllegalArgumentException("At most 100 users per page");
        if (page.isEmpty()) return java.util.Map.of();
        var active = new java.util.HashMap<UUID, Wristband>();
        records.findByActiveUserUuidIn(page.stream().map(FestivalUser::getUserUuid).toList())
                .forEach(record -> active.put(record.getActiveUserUuid(), record));
        var hashes = page.stream().map(this::identity).filter(java.util.Objects::nonNull).distinct().toList();
        var byHash = new java.util.HashMap<String, Wristband>();
        if (!hashes.isEmpty()) records.findBySubjectHashInAndIssuedTrue(hashes)
                .forEach(record -> byHash.put(record.getSubjectHash(), record));
        var result = new java.util.HashMap<UUID, MyStatus>();
        for (FestivalUser user : page) {
            Wristband record = active.get(user.getUserUuid());
            if (record == null) record = byHash.get(identity(user));
            result.put(user.getUserUuid(), new MyStatus(record != null,
                    record == null ? null : record.getIssuedAt(), user.isSchoolVerified()));
        }
        return java.util.Map.copyOf(result);
    }
    @Transactional(readOnly = true)
    public Eligibility eligibility(FestivalRole role, UUID userUuid) {
        requireStaff(role);
        return status(userUuid);
    }
    private Eligibility status(UUID userUuid) {
        FestivalUser user = users.findByUserUuid(userUuid).orElse(null);
        String hash = identity(user);
        Wristband record = records.findByActiveUserUuid(userUuid).orElse(null);
        if (record == null && hash != null) record = records.findBySubjectHash(hash).filter(Wristband::isIssued).orElse(null);
        return new Eligibility(user != null && user.isSchoolVerified(), user != null && user.isStudentFeePaid(),
                hash != null, record != null, record == null ? null : record.getIssuedAt(), record == null ? null : record.getId());
    }
    private String identity(FestivalUser user) {
        if (user == null || !user.isSchoolVerified()) return null;
        String hash = user.getStudentFeeSubjectHash();
        return hash != null && hash.matches("[a-fA-F0-9]{64}") ? hash : null;
    }
    @Transactional
    public Wristband issue(FestivalRole role, UUID actor, String actorName, UUID target) {
        requireStaff(role);
        // Confirm the account still exists upstream before taking the database gate.
        var profile = profiles.getProfile(target);
        fees.lock(); // Same lock order as student verification and local withdrawal.
        FestivalUser user = users.findByUserUuidForUpdate(target).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "FESTIVAL_USER_NOT_FOUND", "축제 사용자를 찾을 수 없습니다."));
        if (!user.isSchoolVerified()) throw new ApiException(HttpStatus.CONFLICT,
                "WRISTBAND_SCHOOL_VERIFICATION_REQUIRED", "학생 인증 완료 후 팔찌를 지급할 수 있습니다.");
        String hash = identity(user);
        if (hash == null) throw new ApiException(HttpStatus.CONFLICT, "WRISTBAND_IDENTITY_REQUIRED",
                "학생 식별정보를 확인할 수 없습니다. 관리자에게 학생 인증 재확인을 요청해 주세요.");
        Wristband record = records.findBySubjectHash(hash).orElse(null);
        if ((record != null && record.isIssued()) || records.findByActiveUserUuid(target).isPresent())
            throw new ApiException(HttpStatus.CONFLICT, "WRISTBAND_ALREADY_ISSUED", "이미 팔찌가 지급된 학생입니다. 중복 지급하지 마세요.");
        if (record == null) record = new Wristband(hash);
        Instant now = clock.instant();
        record.issue(target, display(profile.name()), actor, display(actorName), now);
        records.saveAndFlush(record);
        events.save(new WristbandEvent(record, WristbandEvent.Action.ISSUE, actor, display(actorName), null, now));
        return record;
    }
    @Transactional
    public void revoke(FestivalRole role, UUID actor, String actorName, long id, long version, String reason) {
        requireAdmin(role);
        if (reason == null || reason.isBlank() || reason.length() > 500)
            throw new ApiException(HttpStatus.BAD_REQUEST, "WRISTBAND_REASON_REQUIRED", "철회 사유를 1–500자로 입력해 주세요.");
        fees.lock();
        Wristband record = find(id);
        if (!record.isIssued() || record.getVersion() != version)
            throw new ApiException(HttpStatus.CONFLICT, "WRISTBAND_STATE_CHANGED", "지급 상태가 변경되었습니다. 새로 조회 후 처리해 주세요.");
        Instant now = clock.instant();
        record.revoke(now);
        events.save(new WristbandEvent(record, WristbandEvent.Action.REVOKE, actor, display(actorName), reason.strip(), now));
    }
    public enum StatusFilter { ALL, ISSUED, REVOKED }
    @Transactional(readOnly = true)
    public Page<Wristband> list(FestivalRole role, int page) {
        return list(role, page, StatusFilter.ALL);
    }
    @Transactional(readOnly = true)
    public Page<Wristband> list(FestivalRole role, int page, StatusFilter status) {
        requireAdmin(role);
        var pageable = PageRequest.of(Math.max(0, page), 20, Sort.by(Sort.Direction.DESC, "updatedAt", "id"));
        return status == null || status == StatusFilter.ALL ? records.findAll(pageable)
                : records.findByIssued(status == StatusFilter.ISSUED, pageable);
    }
    @Transactional(readOnly = true)
    public long issuedCount(FestivalRole role) { requireAdmin(role); return records.countByIssuedTrue(); }
    @Transactional(readOnly = true)
    public Wristband detail(FestivalRole role, long id) { requireAdmin(role); return find(id); }
    @Transactional(readOnly = true)
    public Page<WristbandEvent> history(FestivalRole role, long id, int page) {
        requireAdmin(role); find(id);
        return events.findByWristbandIdOrderByIdDesc(id, PageRequest.of(Math.max(0, page), 20));
    }
    private Wristband find(long id) {
        return records.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                "WRISTBAND_NOT_FOUND", "지급 기록을 찾을 수 없습니다."));
    }
    private String display(String name) { return name == null || name.isBlank() ? DeletedUserIdentity.NAME : name; }
}
