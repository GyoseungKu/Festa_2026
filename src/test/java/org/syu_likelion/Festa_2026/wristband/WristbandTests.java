package org.syu_likelion.Festa_2026.wristband;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.hamcrest.Matchers.containsString;

import jakarta.servlet.http.Cookie;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.HttpStatus;
import org.syu_likelion.Festa_2026.admin.*;
import org.syu_likelion.Festa_2026.auth.AuthorizedSsoExecutor.AuthorizedResult;
import org.syu_likelion.Festa_2026.error.ApiException;
import org.syu_likelion.Festa_2026.qr.QrTokenStore;
import org.syu_likelion.Festa_2026.sso.*;
import org.syu_likelion.Festa_2026.sso.SsoProfiles.InternalUserProfile;
import org.syu_likelion.Festa_2026.user.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:wristbands;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "school-sso.subject-hash-secret=wristband-test-secret-at-least-16-bytes"
})
@AutoConfigureMockMvc
class WristbandTests {
    @Autowired WristbandService wristbands;
    @Autowired WristbandRepository records;
    @Autowired WristbandEventRepository events;
    @Autowired FestivalUserRepository users;
    @Autowired FestivalUserService festivalUsers;
    @Autowired FestivalWithdrawalService withdrawal;
    @Autowired QrTokenStore tokens;
    @Autowired MockMvc mvc;
    @Autowired ManualWristbandService manual;
    @Autowired org.syu_likelion.Festa_2026.fee.StudentFeeService fees;
    @Autowired org.syu_likelion.Festa_2026.fee.StudentFeePayerRepository payers;
    @Autowired org.syu_likelion.Festa_2026.schoolsso.SchoolSubjectHasher hasher;
    @MockitoBean SsoInternalProfileClient profiles;
    @MockitoBean AdminAccessService access;
    @MockitoBean UserService authentication;
    private final UUID actor = UUID.randomUUID();
    private static final String SUBJECT = "a".repeat(64);

    @BeforeEach void setup() {
        events.deleteAll(); records.deleteAll(); users.deleteAll(); payers.deleteAll();
        when(profiles.getProfile(any())).thenAnswer(i -> profile(i.getArgument(0)));
        when(profiles.getProfiles(anyList())).thenAnswer(i -> ((List<UUID>) i.getArgument(0)).stream().map(this::profile).toList());
        when(access.authenticate(any(), any())).thenAnswer(i -> {
            String role = i.getArgument(0);
            if (role == null) throw new ApiException(HttpStatus.UNAUTHORIZED, "ADMIN_LOGIN_REQUIRED", "로그인 필요");
            return new AuthorizedResult<>(new AdminAccessService.AdminIdentity(actor, "운영자", FestivalRole.valueOf(role)), null, null);
        });
    }
    private InternalUserProfile profile(UUID id) {
        return new InternalUserProfile(id, "student", "student@example.com", "USER", "ACTIVE", "홍길동", null,
                "2026100001", "컴퓨터공학과", 1, null, null, null, null);
    }
    private UUID student(boolean verified, boolean paid) {
        UUID id = UUID.randomUUID();
        FestivalUser user = new FestivalUser(id);
        if (verified) { user.verifySchool(SUBJECT, Instant.now()); user.updateStudentFee(SUBJECT, paid); }
        users.saveAndFlush(user);
        return id;
    }
    private Wristband issue(UUID id) { return wristbands.issue(FestivalRole.STAFF, actor, "운영자", id); }
    private void assertCode(Runnable action, String code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo(code));
    }
    @Test void unifiedSearchRespectsRoleAndPrefersRegisteredUsers() throws Exception {
        for (String role : List.of("ADMIN", "SUPER_ADMIN")) {
            var cookie = new Cookie("festivalAdminAccess", role);
            mvc.perform(get("/admin/wristbands").cookie(cookie)).andExpect(status().isOk())
                    .andExpect(content().string(org.hamcrest.Matchers.not(containsString("/admin/wristbands/manual/search"))));
            mvc.perform(post("/admin/wristbands/search").cookie(cookie).with(csrf()).param("query", " 2026100001 "))
                    .andExpect(status().isOk()).andExpect(model().attributeExists("manualResult"))
                    .andExpect(model().attributeDoesNotExist("candidates"))
                    .andExpect(content().string(containsString("학번으로 팔찌 지급 처리")));
            for (String query : List.of("홍길동", "2026", "20261000010"))
                mvc.perform(post("/admin/wristbands/search").cookie(cookie).with(csrf()).param("query", query))
                        .andExpect(status().isOk()).andExpect(model().attributeDoesNotExist("manualResult"));
        }
        mvc.perform(post("/admin/wristbands/search").cookie(new Cookie("festivalAdminAccess", "STAFF"))
                        .with(csrf()).param("query", "2026100001"))
                .andExpect(status().isOk()).andExpect(model().attributeDoesNotExist("manualResult"));
        studentWithNumber("2026100001");
        for (String route : List.of("/search", "/manual/search"))
            mvc.perform(post("/admin/wristbands" + route).cookie(new Cookie("festivalAdminAccess", "ADMIN"))
                            .with(csrf()).param("query", "2026100001").param("studentNo", "2026100001"))
                    .andExpect(status().isOk()).andExpect(model().attributeExists("candidates"))
                    .andExpect(model().attributeDoesNotExist("manualResult"))
                    .andExpect(content().string(containsString("홍길동")));
    }

    @Test void manualLookupChecksExactRosterAndDoesNotCreateAccounts() {
        String number = "2026100001";
        assertThat(manual.lookup(FestivalRole.ADMIN, number).studentFeePaid()).isFalse();
        fees.add(FestivalRole.SUPER_ADMIN, actor, number);
        assertThat(manual.lookup(FestivalRole.ADMIN, number).studentFeePaid()).isTrue();
        assertThat(manual.lookup(FestivalRole.ADMIN, "2026100002").studentFeePaid()).isFalse();
        for (String invalid : List.of("홍길동", "2026", "2026**0001", "20261000010"))
            assertCode(() -> manual.lookup(FestivalRole.ADMIN, invalid), "INVALID_WRISTBAND_STUDENT_NO");
        var record = manual.issue(FestivalRole.ADMIN, actor, "관리자", " " + number + " ", null, null);
        assertThat(record.getTargetUserUuid()).isNull();
        assertThat(record.getActiveUserUuid()).isNull();
        assertThat(record.getTargetStudentNo()).isEqualTo(number);
        assertThat(record.getTargetName()).isEqualTo("미입력");
        assertThat(record.getTargetDepartment()).isNull();
        assertThat(manual.lookup(FestivalRole.ADMIN, number).issued()).isTrue();
        assertThat(users.count()).isZero();
        verify(profiles, never()).getProfile(any());
        fees.delete(FestivalRole.SUPER_ADMIN, number);
        assertThat(manual.lookup(FestivalRole.ADMIN, number).studentFeePaid()).isFalse();
    }

    @Test void manualIssueRevokeReissueAndProfileChangesAreAuditedAndVersioned() {
        var record = manual.issue(FestivalRole.ADMIN, actor, "관리자", "2026100001", " 홍길동 ", " 컴퓨터공학과 ");
        long id = record.getId();
        assertCode(() -> manual.issue(FestivalRole.ADMIN, actor, "관리자", "2026100001", null, null), "WRISTBAND_ALREADY_ISSUED");
        assertCode(() -> manual.updateProfile(FestivalRole.ADMIN, actor, "관리자", id, record.getVersion(), "가".repeat(201), null), "INVALID_WRISTBAND_PROFILE");
        manual.updateProfile(FestivalRole.ADMIN, actor, "관리자", id, record.getVersion(), "김학생", "간호학과");
        assertCode(() -> wristbands.revoke(FestivalRole.ADMIN, actor, "관리자", id, record.getVersion(), "예전 화면"), "WRISTBAND_STATE_CHANGED");
        var updated = records.findById(id).orElseThrow();
        assertThat(updated.getTargetName()).isEqualTo("김학생");
        assertThat(updated.getTargetDepartment()).isEqualTo("간호학과");
        assertCode(() -> manual.updateProfile(FestivalRole.ADMIN, actor, "관리자", id, record.getVersion(), null, null), "WRISTBAND_STATE_CHANGED");
        wristbands.revoke(FestivalRole.ADMIN, actor, "관리자", id, updated.getVersion(), "실물 팔찌 회수");
        assertThat(manual.lookup(FestivalRole.ADMIN, "2026100001").issued()).isFalse();
        var reissued = manual.issue(FestivalRole.SUPER_ADMIN, actor, "관리자", "2026100001", "김학생", "간호학과");
        assertThat(reissued.getId()).isEqualTo(id);
        assertThat(events.findByWristbandIdOrderByIdDesc(id, org.springframework.data.domain.Pageable.unpaged()).getContent())
                .extracting(WristbandEvent::getAction).containsExactly(WristbandEvent.Action.ISSUE,
                        WristbandEvent.Action.REVOKE, WristbandEvent.Action.UPDATE_PROFILE, WristbandEvent.Action.ISSUE);
    }

    private UUID studentWithNumber(String number) {
        UUID id = UUID.randomUUID();
        var user = new FestivalUser(id);
        String hash = hasher.hash(number);
        user.verifySchool(hash, Instant.now()); user.updateStudentFee(hash, false);
        users.saveAndFlush(user);
        return id;
    }

    @Test void manualAndRegisteredGrantsShareIdentityAcrossSignupAndReissue() {
        String number = "2026100001";
        var record = manual.issue(FestivalRole.ADMIN, actor, "관리자", number, null, null);
        UUID registered = studentWithNumber(number);
        assertThat(wristbands.mine(registered).issued()).isTrue();
        assertCode(() -> issue(registered), "WRISTBAND_ALREADY_ISSUED");
        wristbands.revoke(FestivalRole.ADMIN, actor, "관리자", record.getId(), record.getVersion(), "회수 확인");
        var memberRecord = issue(registered);
        assertThat(memberRecord.getId()).isEqualTo(record.getId());
        assertThat(memberRecord.getTargetStudentNo()).isNull();
        assertThat(manual.lookup(FestivalRole.ADMIN, number).issued()).isTrue();
        assertCode(() -> manual.issue(FestivalRole.ADMIN, actor, "관리자", number, null, null), "WRISTBAND_ALREADY_ISSUED");
        assertCode(() -> manual.updateProfile(FestivalRole.ADMIN, actor, "관리자", memberRecord.getId(), memberRecord.getVersion(), "이름", null), "WRISTBAND_MANUAL_PROFILE_REQUIRED");
    }

    @Test void concurrentManualAndRegisteredRequestsOnlyGrantOneWristband() throws Exception {
        String number = "2026100001";
        UUID registered = studentWithNumber(number);
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(4)) {
            List<Future<Boolean>> futures = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                boolean useManual = i % 2 == 0;
                futures.add(pool.submit(() -> {
                    start.await();
                    try {
                        if (useManual) manual.issue(FestivalRole.ADMIN, actor, "관리자", number, null, null);
                        else issue(registered);
                        return true;
                    } catch (ApiException e) {
                        assertThat(e.code()).isEqualTo("WRISTBAND_ALREADY_ISSUED"); return false;
                    }
                }));
            }
            start.countDown();
            int successes = 0;
            for (var future : futures) if (future.get(15, TimeUnit.SECONDS)) successes++;
            assertThat(successes).isEqualTo(1);
        }
        assertThat(records.count()).isEqualTo(1); assertThat(events.count()).isEqualTo(1);
    }

    @Test void manualFunctionsEnforceAdminRoleCsrfAndRenderForms() throws Exception {
        for (var role : List.of(FestivalRole.USER, FestivalRole.BOOTH_MANAGER, FestivalRole.STAFF)) {
            assertCode(() -> manual.lookup(role, "2026100001"), "WRISTBAND_MANAGE_FORBIDDEN");
            assertCode(() -> manual.issue(role, actor, "직원", "2026100001", null, null), "WRISTBAND_MANAGE_FORBIDDEN");
            assertCode(() -> manual.updateProfile(role, actor, "직원", 1, 0, null, null), "WRISTBAND_MANAGE_FORBIDDEN");
        }
        var staff = new Cookie("festivalAdminAccess", "STAFF");
        var admin = new Cookie("festivalAdminAccess", "ADMIN");
        mvc.perform(get("/admin/wristbands").cookie(staff)).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("/admin/wristbands/manual/search"))));
        mvc.perform(post("/admin/wristbands/manual/search").cookie(staff).with(csrf()).param("studentNo", "2026100001"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/admin/wristbands/manual/issue").cookie(staff).with(csrf()).param("studentNo", "2026100001"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/admin/wristbands/records/1/profile").cookie(staff).with(csrf()).param("version", "0"))
                .andExpect(status().isForbidden());
        for (String route : List.of("/manual/search", "/manual/issue", "/records/1/profile"))
            mvc.perform(post("/admin/wristbands" + route).cookie(admin).param("studentNo", "2026100001").param("version", "0"))
                    .andExpect(status().isForbidden());
        mvc.perform(post("/admin/wristbands/manual/search").cookie(admin).with(csrf()).param("studentNo", "2026100001"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("미납부")))
                .andExpect(content().string(containsString("학번으로 팔찌 지급 처리")));
        mvc.perform(post("/admin/wristbands/manual/search").cookie(admin).with(csrf()).param("studentNo", "홍길동"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("10자리 숫자")));
        mvc.perform(post("/admin/wristbands/manual/issue").cookie(admin).with(csrf()).param("studentNo", "2026100001")
                        .param("name", "홍학생").param("department", "컴퓨터공학과"))
                .andExpect(status().is3xxRedirection());
        var record = records.findAll().getFirst();
        mvc.perform(get("/admin/wristbands/records/" + record.getId()).cookie(admin)).andExpect(status().isOk())
                .andExpect(content().string(containsString("홍학생"))).andExpect(content().string(containsString("2026100001")))
                .andExpect(content().string(containsString("수령자 정보 저장")));
        mvc.perform(post("/admin/wristbands/manual/search").cookie(admin).with(csrf()).param("studentNo", "2026100001"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("지급 완료")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("학번으로 팔찌 지급 처리"))));
        mvc.perform(post("/admin/wristbands/records/" + record.getId() + "/profile").cookie(admin).with(csrf())
                        .param("version", Long.toString(record.getVersion())).param("name", "김학생").param("department", "간호학과"))
                .andExpect(status().is3xxRedirection());
        mvc.perform(get("/admin/wristbands/records/" + record.getId()).cookie(admin)).andExpect(status().isOk())
                .andExpect(content().string(containsString("김학생"))).andExpect(content().string(containsString("수령자 정보 수정")));
        var updated = records.findById(record.getId()).orElseThrow();
        mvc.perform(post("/admin/wristbands/records/" + record.getId() + "/revoke").cookie(admin).with(csrf())
                        .param("version", Long.toString(updated.getVersion())).param("reason", "팔찌 회수"))
                .andExpect(status().is3xxRedirection());
        mvc.perform(post("/admin/wristbands/manual/search").cookie(new Cookie("festivalAdminAccess", "SUPER_ADMIN"))
                        .with(csrf()).param("studentNo", "2026100001"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("학번으로 팔찌 지급 처리")))
                .andExpect(content().string(containsString("김학생")));
    }
    @Test void managementFiltersCurrentStatusAndKeepsFilterAcrossPages() throws Exception {
        var admin = new Cookie("festivalAdminAccess", "ADMIN");
        for (int i = 0; i < 22; i++)
            manual.issue(FestivalRole.ADMIN, actor, "관리자", Long.toString(2026101000L + i), "학생" + i, null);
        var revoked = records.findBySubjectHash(hasher.hash("2026101000")).orElseThrow();
        wristbands.revoke(FestivalRole.ADMIN, actor, "관리자", revoked.getId(), revoked.getVersion(), "회수");
        assertThat(wristbands.list(FestivalRole.ADMIN, 0, WristbandService.StatusFilter.ALL).getTotalElements()).isEqualTo(22);
        var issued = wristbands.list(FestivalRole.ADMIN, 0, WristbandService.StatusFilter.ISSUED);
        assertThat(issued.getTotalElements()).isEqualTo(21);
        assertThat(issued.getContent()).allMatch(Wristband::isIssued);
        assertThat(wristbands.list(FestivalRole.ADMIN, 1, WristbandService.StatusFilter.ISSUED).getNumberOfElements()).isEqualTo(1);
        assertThat(wristbands.list(FestivalRole.ADMIN, 0, WristbandService.StatusFilter.REVOKED).getContent())
                .extracting(Wristband::getId).containsExactly(revoked.getId());
        mvc.perform(get("/admin/wristbands/manage").cookie(admin).param("status", "ISSUED"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("조회 결과 21건")))
                .andExpect(content().string(containsString("status=ISSUED")));
        mvc.perform(get("/admin/wristbands/manage").cookie(admin).param("status", "ISSUED").param("page", "1"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("status=ISSUED")));
        mvc.perform(get("/admin/wristbands/manage").cookie(admin).param("status", "REVOKED"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("조회 결과 1건")))
                .andExpect(content().string(containsString("현재 지급 완료 21명")));
        assertCode(() -> wristbands.list(FestivalRole.STAFF, 0, WristbandService.StatusFilter.REVOKED), "WRISTBAND_MANAGE_FORBIDDEN");
        manual.issue(FestivalRole.ADMIN, actor, "관리자", "2026101000", null, null);
        assertThat(wristbands.list(FestivalRole.ADMIN, 0, WristbandService.StatusFilter.REVOKED).getTotalElements()).isZero();
        assertThat(wristbands.list(FestivalRole.ADMIN, 0, WristbandService.StatusFilter.ISSUED).getTotalElements()).isEqualTo(22);
    }

    @Test void unpaidVerifiedStudentsCanReceiveAndRevocationRequiresAdminAndReason() {
        UUID id = student(true, false);
        var issued = issue(id);
        assertThat(wristbands.mine(id).issued()).isTrue();
        assertThat(wristbands.eligibility(FestivalRole.STAFF, id).studentFeePaid()).isFalse();
        assertCode(() -> issue(id), "WRISTBAND_ALREADY_ISSUED");
        assertCode(() -> wristbands.revoke(FestivalRole.STAFF, actor, "직원", issued.getId(), issued.getVersion(), "실수"), "WRISTBAND_MANAGE_FORBIDDEN");
        assertCode(() -> wristbands.revoke(FestivalRole.ADMIN, actor, "관리자", issued.getId(), issued.getVersion(), "  "), "WRISTBAND_REASON_REQUIRED");
        wristbands.revoke(FestivalRole.ADMIN, actor, "관리자", issued.getId(), issued.getVersion(), "오지급 정정");
        assertThat(wristbands.mine(id).issued()).isFalse();
        assertThat(wristbands.mine(id).issuedAt()).isNull();
        assertThat(wristbands.issuedCount(FestivalRole.ADMIN)).isZero();
        var reissued = issue(id);
        assertThat(reissued.getId()).isEqualTo(issued.getId());
        assertThat(events.count()).isEqualTo(3);
        assertThat(wristbands.history(FestivalRole.ADMIN, issued.getId(), 0).getContent())
                .extracting(WristbandEvent::getAction).containsExactly(WristbandEvent.Action.ISSUE, WristbandEvent.Action.REVOKE, WristbandEvent.Action.ISSUE);
        assertCode(() -> wristbands.revoke(FestivalRole.ADMIN, actor, "관리자", issued.getId(), issued.getVersion(), "오래된 화면"), "WRISTBAND_STATE_CHANGED");
        assertThat(wristbands.mine(id).issued()).isTrue();
    }
    @Test void verificationIsRequiredAndBoothManagersCannotIssueOrManage() {
        UUID id = student(false, false);
        assertCode(() -> issue(id), "WRISTBAND_SCHOOL_VERIFICATION_REQUIRED");
        for (var role : List.of(FestivalRole.USER, FestivalRole.BOOTH_MANAGER)) {
            assertCode(() -> wristbands.issue(role, actor, "사용자", id), "WRISTBAND_FORBIDDEN");
        }
        assertCode(() -> wristbands.list(FestivalRole.STAFF, 0), "WRISTBAND_MANAGE_FORBIDDEN");
        var user = users.findByUserUuid(id).orElseThrow();
        user.verifySchoolByAdmin(Instant.now()); users.saveAndFlush(user);
        assertCode(() -> issue(id), "WRISTBAND_IDENTITY_REQUIRED");
        assertThat(records.count()).isZero(); assertThat(events.count()).isZero();
    }
    @Test void withdrawalAnonymizesButRetainsSubjectAndPreventsNewUuidDuplicate() {
        UUID old = student(true, true);
        var record = issue(old);
        withdrawal.withdraw(old);
        assertThat(users.findByUserUuid(old)).isEmpty();
        var retained = records.findById(record.getId()).orElseThrow();
        assertThat(retained.isIssued()).isTrue();
        assertThat(retained.getTargetName()).isEqualTo("알 수 없음");
        assertThat(retained.getTargetUserUuid()).isNotEqualTo(old);
        UUID rejoined = student(true, false);
        assertThat(wristbands.mine(rejoined).issued()).isTrue();
        assertCode(() -> issue(rejoined), "WRISTBAND_ALREADY_ISSUED");
        wristbands.revoke(FestivalRole.ADMIN, actor, "관리자", retained.getId(), retained.getVersion(), "팔찌 회수 확인");
        issue(rejoined);
        assertThat(records.count()).isEqualTo(1);
        assertThat(events.count()).isEqualTo(3);
    }
    @Test void concurrentIssueRequestsOnlyCreateOneGrantAndOneEvent() throws Exception {
        UUID id = student(true, false);
        UUID sameStudent = UUID.randomUUID();
        FestivalUser manuallyVerified = new FestivalUser(sameStudent);
        manuallyVerified.verifySchoolByAdmin(Instant.now());
        manuallyVerified.updateStudentFee(SUBJECT, false);
        users.saveAndFlush(manuallyVerified);
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(4)) {
            List<Future<Boolean>> futures = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                UUID target = i % 2 == 0 ? id : sameStudent;
                futures.add(pool.submit(() -> {
                start.await();
                try { issue(target); return true; }
                catch (ApiException e) { assertThat(e.code()).isEqualTo("WRISTBAND_ALREADY_ISSUED"); return false; }
                }));
            }
            start.countDown();
            int successes = 0;
            for (var future : futures) if (future.get(15, TimeUnit.SECONDS)) successes++;
            assertThat(successes).isEqualTo(1);
        }
        assertThat(records.count()).isEqualTo(1); assertThat(events.count()).isEqualTo(1);
    }
    @Test void currentReceiptRemainsVisibleAfterStudentVerificationIsRevoked() {
        UUID id = student(true, false); issue(id);
        festivalUsers.revokeSchoolVerification(id);
        assertThat(wristbands.mine(id).issued()).isTrue();
        assertThat(wristbands.mine(id).schoolVerified()).isFalse();
        assertCode(() -> issue(id), "WRISTBAND_SCHOOL_VERIFICATION_REQUIRED");
    }
    @Test void adminPagesEnforceRoleCsrfAndRenderSearchQrHistory() throws Exception {
        UUID id = student(true, false);
        var staff = new Cookie("festivalAdminAccess", "STAFF");
        var admin = new Cookie("festivalAdminAccess", "ADMIN");
        mvc.perform(get("/admin/wristbands").cookie(staff)).andExpect(status().isOk())
                .andExpect(content().string(containsString("팔찌 지급")));
        mvc.perform(get("/admin/wristbands/manage").cookie(staff)).andExpect(status().isForbidden());
        mvc.perform(get("/admin/wristbands").cookie(new Cookie("festivalAdminAccess", "BOOTH_MANAGER"))).andExpect(status().isForbidden());
        mvc.perform(post("/admin/wristbands/issue").cookie(staff).param("userUuid", id.toString())).andExpect(status().isForbidden());
        mvc.perform(post("/admin/wristbands/search").cookie(staff).with(csrf()).param("query", "홍길동"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("미납부")))
                .andExpect(content().string(containsString("인증 완료")));
        tokens.save("test-wristband-qr", id, Duration.ofMinutes(1));
        mvc.perform(post("/admin/wristbands/scan").cookie(staff).with(csrf()).param("token", "test-wristband-qr"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("팔찌 지급 처리")));
        mvc.perform(post("/admin/wristbands/issue").cookie(staff).with(csrf()).param("userUuid", id.toString()))
                .andExpect(status().is3xxRedirection());
        var band = records.findAll().getFirst();
        mvc.perform(get("/admin/wristbands/manage").cookie(admin)).andExpect(status().isOk())
                .andExpect(content().string(containsString("현재 지급 완료 1명")));
        mvc.perform(get("/admin/wristbands/records/" + band.getId()).cookie(admin)).andExpect(status().isOk())
                .andExpect(content().string(containsString("운영자")));
        mvc.perform(post("/admin/wristbands/records/" + band.getId() + "/revoke").cookie(staff).with(csrf())
                        .param("version", Long.toString(band.getVersion())).param("reason", "철회"))
                .andExpect(status().isForbidden());
    }
    @Test void userManagementShowsCurrentWristbandStateWithoutChangingQrJson() throws Exception {
        UUID id = student(true, false);
        var staff = new Cookie("festivalAdminAccess", "STAFF");
        mvc.perform(post("/admin/qr/search").cookie(staff).with(csrf()).param("query", "홍길동"))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.matchesPattern("(?s).*<dt>입장 팔찌</dt>\\s*<dd>미지급</dd>.*")));
        var band = issue(id);
        tokens.save("management-qr", id, Duration.ofMinutes(1));
        mvc.perform(post("/admin/qr/scan").cookie(staff).with(csrf()).param("token", "management-qr"))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.matchesPattern("(?s).*<dt>입장 팔찌</dt>\\s*<dd>지급 완료</dd>.*")));
        mvc.perform(post("/admin/qr/search").cookie(staff).with(csrf()).param("query", "홍길동"))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.matchesPattern("(?s).*<dt>입장 팔찌</dt>\\s*<dd>지급 완료</dd>.*")));
        var me = new UserDtos.MeResponse(actor, "staff", null, "USER", "ACTIVE", null, null,
                null, null, null, null, null, null, null, Set.of(FestivalRole.STAFF));
        when(authentication.getMe("staff-api", null)).thenReturn(new AuthorizedResult<>(me, null, null));
        mvc.perform(post("/api/qr/scan").header("Authorization", "Bearer staff-api")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content("{\"token\":\"management-qr\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.wristband").doesNotExist())
                .andExpect(jsonPath("$.userUuid").doesNotExist());
        wristbands.revoke(FestivalRole.ADMIN, actor, "관리자", band.getId(), band.getVersion(), "오지급");
        mvc.perform(post("/admin/qr/search").cookie(staff).with(csrf()).param("query", "홍길동"))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.matchesPattern("(?s).*<dt>입장 팔찌</dt>\\s*<dd>미지급</dd>.*")));
    }

    @Test void studentVerificationAndFeesAreVisibleOnlyToStaffAndAbove() throws Exception {
        UUID id = student(true, true);
        tokens.save("privacy-qr", id, Duration.ofMinutes(1));
        for (var role : List.of(FestivalRole.BOOTH_MANAGER, FestivalRole.STAFF, FestivalRole.ADMIN, FestivalRole.SUPER_ADMIN)) {
            var me = new UserDtos.MeResponse(actor, "operator", null, "USER", "ACTIVE", null, null,
                    null, null, null, null, null, null, null, Set.of(role));
            when(authentication.getMe(role.name(), null)).thenReturn(new AuthorizedResult<>(me, null, null));
            var api = mvc.perform(post("/api/qr/scan").header("Authorization", "Bearer " + role.name())
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content("{\"token\":\"privacy-qr\"}"))
                    .andExpect(status().isOk());
            var html = mvc.perform(post("/admin/qr/scan").cookie(new Cookie("festivalAdminAccess", role.name()))
                    .with(csrf()).param("token", "privacy-qr")).andExpect(status().isOk());
            if (role == FestivalRole.BOOTH_MANAGER) {
                for (String field : List.of("schoolVerificationStatus", "schoolVerified", "schoolVerifiedAt", "studentFeePaid")) {
                    api.andExpect(jsonPath("$." + field).doesNotExist());
                }
                html.andExpect(content().string(org.hamcrest.Matchers.not(containsString("<dt>학교 인증"))))
                        .andExpect(content().string(org.hamcrest.Matchers.not(containsString("<dt>학생회비</dt>"))));
            } else {
                api.andExpect(jsonPath("$.schoolVerificationStatus").value("VERIFIED"))
                        .andExpect(jsonPath("$.schoolVerified").value(true))
                        .andExpect(jsonPath("$.schoolVerifiedAt").isNotEmpty())
                        .andExpect(jsonPath("$.studentFeePaid").value(true));
                html.andExpect(content().string(containsString("<dt>학교 인증</dt><dd>VERIFIED</dd>")))
                        .andExpect(content().string(containsString("<dt>학생회비</dt><dd>납부</dd>")));
            }
        }
    }

    @Test void ownApiUsesAuthenticatedUuidAndPreservesTokenRotation() throws Exception {
        UUID id = student(true, false); issue(id);
        var me = new UserDtos.MeResponse(id, "student", null, "USER", "ACTIVE", null, null,
                null, null, null, null, null, null, null, Set.of(FestivalRole.USER));
        when(authentication.getMe("self", null)).thenReturn(new AuthorizedResult<>(me, "rotated-access", "rotated-refresh"));
        mvc.perform(get("/api/users/me/wristband")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/users/me/wristband").header("Authorization", "Bearer self")
                        .param("userUuid", UUID.randomUUID().toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.issued").value(true))
                .andExpect(jsonPath("$.issuedAt").isNotEmpty()).andExpect(jsonPath("$.subjectHash").doesNotExist())
                .andExpect(jsonPath("$.issuedBy").doesNotExist())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("X-Access-Token", "rotated-access"))
                .andExpect(header().string("Set-Cookie", containsString("rotated-refresh")));
    }
}
