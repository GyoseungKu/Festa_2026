package org.syu_likelion.Festa_2026.fee;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import java.time.Instant;
import java.util.*;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.syu_likelion.Festa_2026.admin.*;
import org.syu_likelion.Festa_2026.auth.AuthorizedSsoExecutor.AuthorizedResult;
import org.syu_likelion.Festa_2026.user.*;
import org.syu_likelion.Festa_2026.schoolsso.*;
import org.syu_likelion.Festa_2026.sso.*;
import org.syu_likelion.Festa_2026.error.ApiException;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:fees;MODE=MySQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class StudentFeeTests {
    @Autowired StudentFeeService fees;
    @Autowired StudentFeePayerRepository payers;
    @Autowired FestivalUserService users;
    @Autowired FestivalUserRepository userRepository;
    @Autowired MockMvc mvc;
    @MockitoBean SchoolSubjectHasher hasher;
    @MockitoBean SsoInternalProfileClient profiles;
    @MockitoBean AdminAccessService access;
    final UUID actor = UUID.randomUUID();
    @BeforeEach void setup() {
        payers.deleteAll(); userRepository.deleteAll();
        when(hasher.hash(anyString())).thenAnswer(i -> String.format("%064d", Long.parseLong(i.getArgument(0))));
        when(access.authenticate("admin", null)).thenReturn(new AuthorizedResult<>(new AdminAccessService.AdminIdentity(actor, "관리자", FestivalRole.ADMIN), null, null));
        when(access.authenticate("staff", null)).thenReturn(new AuthorizedResult<>(new AdminAccessService.AdminIdentity(actor, "스태프", FestivalRole.STAFF), null, null));
    }
    SchoolAcademicProfile school(String number) {
        return new SchoolAcademicProfile(number, "학과", "이름", "총학생회", Instant.now(), Instant.now().plusSeconds(300));
    }
    @Test void normalizesDeduplicatesAndRejectsEntireInvalidBatch() {
        var result = fees.add(FestivalRole.ADMIN, actor, "2022**0062\n2022100062\t2024100920");
        assertThat(result.added()).isEqualTo(2);
        assertThat(result.duplicates()).isEqualTo(1);
        assertThat(payers.existsById("2022100062")).isTrue();
        assertThatThrownBy(() -> fees.add(FestivalRole.ADMIN, actor, "2025100001\n학번" )).isInstanceOf(ApiException.class);
        assertThat(payers.count()).isEqualTo(2);
        assertThatThrownBy(() -> fees.add(FestivalRole.STAFF, actor, "2025100001")).isInstanceOf(ApiException.class);
    }
    @Test void verificationRosterChangesAndRevocationStayConsistent() {
        UUID id = UUID.randomUUID();
        users.linkAndGetProfile(id);
        fees.add(FestivalRole.ADMIN, actor, "2024100920");
        assertThat(users.getProfile(id).studentFeePaid()).isFalse();
        assertThat(users.verifySchool(id, school("2024100920")).studentFeePaid()).isTrue();
        fees.delete(FestivalRole.ADMIN, "2024**0920");
        assertThat(users.getProfile(id).schoolVerified()).isTrue();
        assertThat(users.getProfile(id).studentFeePaid()).isFalse();
        fees.add(FestivalRole.ADMIN, actor, "2024100920");
        assertThat(users.getProfile(id).studentFeePaid()).isTrue();
        users.revokeSchoolVerification(id);
        assertThat(users.getProfile(id).studentFeePaid()).isFalse();
        fees.add(FestivalRole.ADMIN, actor, "2024100920");
        assertThat(users.getProfile(id).studentFeePaid()).isFalse();
    }
    @Test void existingVerifiedStudentsAreRecheckedAndNewIdentityDoesNotReusePayment() {
        UUID id = UUID.randomUUID();
        users.verifySchool(id, school("2024100920"));
        fees.add(FestivalRole.SUPER_ADMIN, actor, "2024100920");
        assertThat(users.getProfile(id).studentFeePaid()).isTrue();
        users.verifySchool(id, school("2024100921"));
        assertThat(users.getProfile(id).studentFeePaid()).isFalse();
    }
    @Test void manualVerificationUsesServerProfile() {
        UUID id = UUID.randomUUID(); users.linkAndGetProfile(id);
        var profile = new SsoProfiles.InternalUserProfile(id, null, null, null, null, null, null, "2024100920", null, null, null, null, null, null);
        when(profiles.getProfile(id)).thenReturn(profile);
        fees.add(FestivalRole.ADMIN, actor, "2024100920");
        assertThat(users.updateSchoolVerificationByAdmin(FestivalRole.ADMIN, id, true).studentFeePaid()).isTrue();
        assertThat(users.updateSchoolVerificationByAdmin(FestivalRole.ADMIN, id, false).studentFeePaid()).isFalse();
    }

    @Test void legacyManualVerificationBackfillsAndFailureRollsBackRoster() {
        UUID id = UUID.randomUUID();
        FestivalUser legacy = new FestivalUser(id);
        legacy.verifySchoolByAdmin(Instant.now());
        userRepository.saveAndFlush(legacy);
        when(profiles.getProfiles(List.of(id))).thenReturn(List.of());
        assertThatThrownBy(() -> fees.add(FestivalRole.ADMIN, actor, "2024100920")).isInstanceOf(ApiException.class);
        assertThat(payers.count()).isZero();
        when(profiles.getProfiles(List.of(id))).thenReturn(List.of(new SsoProfiles.InternalUserProfile(
                id, null, null, null, null, null, null, "2024100920", null, null, null, null, null, null)));
        fees.add(FestivalRole.ADMIN, actor, "2024100920");
        assertThat(users.getProfile(id).studentFeePaid()).isTrue();
    }

    @Test void concurrentVerificationAndRosterImportProduceMatchingState() throws Exception {
        UUID id = UUID.randomUUID(); users.linkAndGetProfile(id);
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var verification = executor.submit(() -> { start.await(); return users.verifySchool(id, school("2024100920")); });
            var registration = executor.submit(() -> { start.await(); return fees.add(FestivalRole.ADMIN, actor, "2024100920"); });
            start.countDown();
            verification.get(15, java.util.concurrent.TimeUnit.SECONDS);
            registration.get(15, java.util.concurrent.TimeUnit.SECONDS);
        }
        assertThat(users.getProfile(id).studentFeePaid()).isTrue();
    }
    @Test void adminScreenSupportsPostAndPaginationAndProtectsWrites() throws Exception {
        Cookie admin = new Cookie("festivalAdminAccess", "admin");
        mvc.perform(post("/admin/student-fees").cookie(admin).with(csrf()).param("studentNumbers", "2024100920"))
                .andExpect(status().is3xxRedirection());
        fees.add(FestivalRole.ADMIN, actor, java.util.stream.IntStream.range(1, 25).mapToObj(i -> "202410" + String.format("%04d", i)).collect(java.util.stream.Collectors.joining("\n")));
        mvc.perform(get("/admin/student-fees").cookie(admin)).andExpect(status().isOk());
        assertThat(fees.list(FestivalRole.ADMIN, "202410", 0).getContent()).hasSize(20);
        assertThat(fees.list(FestivalRole.ADMIN, "202410", 1).getContent()).hasSize(5);
        mvc.perform(get("/admin/student-fees").cookie(new Cookie("festivalAdminAccess", "staff"))).andExpect(status().isForbidden());
        mvc.perform(post("/admin/student-fees/delete").cookie(admin).param("studentNo", "2024100920")).andExpect(status().isForbidden());
        mvc.perform(post("/admin/student-fees/delete").cookie(admin).with(csrf()).param("studentNo", "2024100920")).andExpect(status().is3xxRedirection());
        assertThat(payers.existsById("2024100920")).isFalse();
    }
}
