package org.syu_likelion.Festa_2026.fee;

import java.time.Clock;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.syu_likelion.Festa_2026.error.ApiException;
import org.syu_likelion.Festa_2026.schoolsso.SchoolSubjectHasher;
import org.syu_likelion.Festa_2026.sso.SsoInternalProfileClient;
import org.syu_likelion.Festa_2026.user.*;

@Service
public class StudentFeeService {
    private final StudentFeePayerRepository payers;
    private final StudentFeeLockRepository locks;
    private final FestivalUserRepository users;
    private final SchoolSubjectHasher hasher;
    private final SsoInternalProfileClient profiles;
    private final Clock clock;
    public StudentFeeService(StudentFeePayerRepository payers, StudentFeeLockRepository locks,
            FestivalUserRepository users, SchoolSubjectHasher hasher, SsoInternalProfileClient profiles, Clock clock) {
        this.payers = payers; this.locks = locks; this.users = users;
        this.hasher = hasher; this.profiles = profiles; this.clock = clock;
    }
    public record ImportResult(int added, int duplicates, int rechecked) { }
    public static void requireSuperAdmin(FestivalRole role) {
        if (role != FestivalRole.SUPER_ADMIN)
            throw new ApiException(HttpStatus.FORBIDDEN, "STUDENT_FEE_MANAGE_FORBIDDEN", "납부자 명단은 SUPER_ADMIN만 관리할 수 있습니다.");
    }
    public static String normalizeNumber(String value) {
        String number = value == null ? "" : value.strip();
        if (number.matches("[0-9]{4}\\*\\*[0-9]{4}")) number = number.substring(0, 4) + "10" + number.substring(6);
        if (!number.matches("[0-9]{10}"))
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_STUDENT_FEE_NUMBER", "학번은 10자리 숫자 또는 2022**0062 형식이어야 합니다.");
        return number;
    }
    @Transactional(propagation = Propagation.MANDATORY)
    public void lock() {
        locks.lockRoster().orElseThrow(() -> new IllegalStateException("Student fee lock is not initialized"));
    }
    /** 학생인증 처리 트랜잭션에서 호출. 학교 인증 학번만 사용한다. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void verify(FestivalUser user, String studentNo) {
        String hash = studentNo != null && studentNo.strip().matches("[0-9]{10}") ? hasher.hash(studentNo.strip()) : null;
        user.updateStudentFee(hash == null ? "" : hash, hash != null && payers.findAllForUpdate().stream().anyMatch(p -> hash.equals(p.getSubjectHash())));
    }
    @Transactional(propagation = Propagation.MANDATORY)
    public void verifyByAdmin(FestivalUser user) {
        verify(user, profiles.getProfile(user.getUserUuid()).studentNo());
    }
    @Transactional(readOnly = true)
    public Page<StudentFeePayer> list(FestivalRole role, String query, int page) {
        requireSuperAdmin(role);
        String term = query == null ? "" : query.strip();
        if (term.contains("*")) term = normalizeNumber(term);
        if (!term.matches("[0-9]{0,10}"))
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_STUDENT_FEE_QUERY", "검색은 학번 숫자 또는 마스킹 학번으로 입력해 주세요.");
        return payers.findByStudentNoContaining(term, PageRequest.of(Math.max(0, page), 20, Sort.by("studentNo")));
    }
    @Transactional
    public ImportResult add(FestivalRole role, UUID actor, String text) {
        requireSuperAdmin(role);
        if (text == null || text.isBlank() || text.length() > 200000)
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_STUDENT_FEE_INPUT", "학번을 입력해 주세요. 한 번에 최대 10,000개를 등록할 수 있습니다.");
        String[] tokens = text.strip().split("[\\s,;]+");
        if (tokens.length > 10000) throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_STUDENT_FEE_INPUT", "한 번에 최대 10,000개를 등록할 수 있습니다.");
        Set<String> numbers = new LinkedHashSet<>();
        for (int i = 0; i < tokens.length; i++) {
            try { numbers.add(normalizeNumber(tokens[i])); }
            catch (ApiException invalid) { throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_STUDENT_FEE_NUMBER", (i + 1) + "번째 항목의 학번 형식을 확인해 주세요. 저장된 항목은 없습니다."); }
        }
        lock();
        Set<String> existing = new HashSet<>();
        payers.findAllForUpdate().forEach(p -> existing.add(p.getStudentNo()));
        int added = 0;
        for (String number : numbers) if (!existing.contains(number)) {
            payers.save(new StudentFeePayer(number, hasher.hash(number), actor.toString(), clock.instant())); added++;
        }
        payers.flush();
        return new ImportResult(added, tokens.length - added, recheck());
    }
    @Transactional
    public int delete(FestivalRole role, String number) {
        requireSuperAdmin(role);
        String normalized = normalizeNumber(number);
        lock();
        payers.deleteById(normalized);
        payers.flush();
        return recheck();
    }
    private int recheck() {
        List<FestivalUser> linked = users.findAllForFeeUpdate();
        // 이전 관리자 인증 사용자는 학교 식별 해시가 없을 수 있다.
        List<FestivalUser> missing = linked.stream().filter(u -> u.isSchoolVerified() && u.getStudentFeeSubjectHash() == null).toList();
        for (int start = 0; start < missing.size(); start += 100) {
            var batch = missing.subList(start, Math.min(start + 100, missing.size()));
            Map<UUID, String> numbers = new HashMap<>();
            profiles.getProfiles(batch.stream().map(FestivalUser::getUserUuid).toList()).forEach(p -> numbers.put(p.userUuid(), p.studentNo()));
            for (var user : batch) {
                if (!numbers.containsKey(user.getUserUuid())) throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "STUDENT_FEE_RECHECK_FAILED", "사용자 학번을 확인하지 못했습니다. 다시 시도해 주세요.");
                String number = numbers.get(user.getUserUuid());
                String hash = number != null && number.strip().matches("[0-9]{10}") ? hasher.hash(number.strip()) : "";
                user.updateStudentFee(hash, false);
            }
        }
        Set<String> hashes = new HashSet<>();
        payers.findAllForUpdate().forEach(p -> hashes.add(p.getSubjectHash()));
        linked.forEach(u -> u.updateStudentFee(u.getStudentFeeSubjectHash(), hashes.contains(u.getStudentFeeSubjectHash())));
        return linked.size();
    }
}
