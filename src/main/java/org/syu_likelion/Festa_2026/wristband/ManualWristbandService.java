package org.syu_likelion.Festa_2026.wristband;

import java.time.Clock;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.syu_likelion.Festa_2026.error.ApiException;
import org.syu_likelion.Festa_2026.fee.StudentFeePayerRepository;
import org.syu_likelion.Festa_2026.fee.StudentFeeService;
import org.syu_likelion.Festa_2026.schoolsso.SchoolSubjectHasher;
import org.syu_likelion.Festa_2026.user.FestivalRole;

/** ADMIN 확인으로 처리하는 학번 직접 지급. 로그인 계정이나 학생 인증을 생성하지 않는다. */
@Service
public class ManualWristbandService {
    private final WristbandRepository records;
    private final WristbandEventRepository events;
    private final StudentFeePayerRepository payers;
    private final StudentFeeService fees;
    private final SchoolSubjectHasher hasher;
    private final Clock clock;

    public ManualWristbandService(WristbandRepository records, WristbandEventRepository events,
            StudentFeePayerRepository payers, StudentFeeService fees, SchoolSubjectHasher hasher, Clock clock) {
        this.records = records; this.events = events; this.payers = payers;
        this.fees = fees; this.hasher = hasher; this.clock = clock;
    }

    public record Lookup(String studentNo, boolean studentFeePaid, Wristband wristband) {
        public boolean issued() { return wristband != null && wristband.isIssued(); }
    }

    private String number(String value) {
        String normalized = value == null ? "" : value.strip();
        if (!normalized.matches("[0-9]{10}"))
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_WRISTBAND_STUDENT_NO", "학번은 마스킹 없이 10자리 숫자로 입력해 주세요.");
        return normalized;
    }

    @Transactional(readOnly = true)
    public Lookup lookup(FestivalRole role, String studentNo) {
        WristbandService.requireAdmin(role);
        String normalized = number(studentNo);
        return new Lookup(normalized, payers.existsById(normalized),
                records.findBySubjectHash(hasher.hash(normalized)).orElse(null));
    }

    @Transactional
    public Wristband issue(FestivalRole role, UUID actor, String actorName, String studentNo,
            String name, String department) {
        WristbandService.requireAdmin(role);
        String normalized = number(studentNo);
        String selectedName = optional(name, 200);
        String selectedDepartment = optional(department, 100);
        fees.lock();
        String hash = hasher.hash(normalized);
        Wristband record = records.findBySubjectHash(hash).orElse(null);
        if (record != null && record.isIssued())
            throw new ApiException(HttpStatus.CONFLICT, "WRISTBAND_ALREADY_ISSUED", "이미 팔찌가 지급된 학생입니다. 중복 지급하지 마세요.");
        if (record == null) record = new Wristband(hash);
        var now = clock.instant();
        record.issueWithoutAccount(normalized, selectedName == null ? "미입력" : selectedName,
                selectedDepartment, actor, actorName, now);
        records.saveAndFlush(record);
        events.save(new WristbandEvent(record, WristbandEvent.Action.ISSUE, actor, actorName, null, now));
        return record;
    }

    @Transactional
    public void updateProfile(FestivalRole role, UUID actor, String actorName, long id, long version,
            String name, String department) {
        WristbandService.requireAdmin(role);
        String selectedName = optional(name, 200);
        String selectedDepartment = optional(department, 100);
        fees.lock();
        Wristband record = records.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                "WRISTBAND_NOT_FOUND", "지급 기록을 찾을 수 없습니다."));
        if (record.getTargetUserUuid() != null || record.getTargetStudentNo() == null)
            throw new ApiException(HttpStatus.CONFLICT, "WRISTBAND_MANUAL_PROFILE_REQUIRED", "학번 직접 지급 기록에서만 수령자 정보를 수정할 수 있습니다.");
        if (record.getVersion() != version)
            throw new ApiException(HttpStatus.CONFLICT, "WRISTBAND_STATE_CHANGED", "지급 상태가 변경되었습니다. 새로 조회 후 처리해 주세요.");
        var now = clock.instant();
        record.updateManualProfile(selectedName == null ? "미입력" : selectedName, selectedDepartment, now);
        records.saveAndFlush(record);
        events.save(new WristbandEvent(record, WristbandEvent.Action.UPDATE_PROFILE, actor, actorName, null, now));
    }

    private String optional(String value, int max) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.strip();
        if (normalized.length() > max)
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_WRISTBAND_PROFILE", "이름은 200자, 학과는 100자 이내로 입력해 주세요.");
        return normalized;
    }
}
