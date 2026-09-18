package org.syu_likelion.Festa_2026.qr;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.containsString;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.servlet.http.Cookie;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.syu_likelion.Festa_2026.admin.*;
import org.syu_likelion.Festa_2026.auth.AuthorizedSsoExecutor.AuthorizedResult;
import org.syu_likelion.Festa_2026.error.ApiException;
import org.syu_likelion.Festa_2026.sso.*;
import org.syu_likelion.Festa_2026.sso.SsoProfiles.InternalUserProfile;
import org.syu_likelion.Festa_2026.user.*;
import org.syu_likelion.Festa_2026.wristband.*;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:directory;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.properties.hibernate.generate_statistics=true"})
@AutoConfigureMockMvc
@Transactional
class UserDirectoryTests {
    @Autowired UserDirectoryService directory;
    @Autowired FestivalUserRepository users;
    @Autowired WristbandRepository wristbands;
    @Autowired EntityManager em;
    @Autowired EntityManagerFactory emf;
    @Autowired MockMvc mvc;
    @MockitoBean SsoInternalProfileClient profiles;
    @MockitoBean AdminAccessService access;

    @BeforeEach void setup() {
        when(profiles.getProfiles(anyList())).thenAnswer(call -> {
            List<UUID> ids = call.getArgument(0);
            return ids.stream().map(this::profile).toList();
        });
        when(access.authenticate(any(), any())).thenAnswer(call -> new AuthorizedResult<>(
                new AdminAccessService.AdminIdentity(UUID.randomUUID(), "운영자", FestivalRole.valueOf(call.getArgument(0))), null, null));
    }

    private InternalUserProfile profile(UUID id) {
        return new InternalUserProfile(id, "private-login", "private@example.com", "USER", "ACTIVE", "홍길동",
                "01012345678", "2026100001", "컴퓨터공학과", 1, null, null, null, null);
    }

    private FestivalUser member(FestivalRole role, boolean verified, boolean paid) {
        FestivalUser user = new FestivalUser(UUID.randomUUID());
        user.addRole(role);
        if (verified) {
            String hash = user.getUserUuid().toString().replace("-", "").repeat(2);
            user.verifySchool(hash, Instant.now());
            user.updateStudentFee(hash, paid);
        }
        return users.saveAndFlush(user);
    }

    @Test void paginationBoundsProfilesAndDatabaseQueries() {
        for (int i = 0; i < 105; i++) member(FestivalRole.USER, false, false);
        em.clear();
        var stats = emf.unwrap(SessionFactory.class).getStatistics();
        stats.clear();
        var first = directory.list(FestivalRole.ADMIN, null, null, null, 0, 10000);
        assertThat(first.getSize()).isEqualTo(100);
        assertThat(first.getNumberOfElements()).isEqualTo(100);
        assertThat(first.getTotalElements()).isEqualTo(105);
        assertThat(stats.getPrepareStatementCount()).isLessThanOrEqualTo(4);
        @SuppressWarnings("unchecked") ArgumentCaptor<List<UUID>> ids = ArgumentCaptor.forClass(List.class);
        verify(profiles).getProfiles(ids.capture());
        assertThat(ids.getValue()).hasSize(100);
        var second = directory.list(FestivalRole.ADMIN, null, null, null, 1, 100);
        assertThat(second.getNumberOfElements()).isEqualTo(5);
        assertThat(second.getContent().stream().map(QrDtos.QrUserView::userUuid).toList())
                .doesNotContainAnyElementsOf(ids.getValue());
    }

    @Test void filtersApplyBeforePaginationAndUseEffectiveVerificationAndPayment() {
        var target = member(FestivalRole.STAFF, true, true);
        target.addRole(FestivalRole.BOOTH_MANAGER);
        users.saveAndFlush(target);
        member(FestivalRole.USER, false, false);
        var revoked = member(FestivalRole.ADMIN, true, true);
        revoked.revokeSchoolVerification(); users.saveAndFlush(revoked);
        assertThat(directory.list(FestivalRole.ADMIN, FestivalRole.BOOTH_MANAGER, true, true, 0, 20).getContent())
                .extracting(QrDtos.QrUserView::userUuid).containsExactly(target.getUserUuid());
        assertThat(directory.list(FestivalRole.ADMIN, null, false, false, 0, 20).getTotalElements()).isEqualTo(2);
        clearInvocations(profiles);
        assertThat(directory.list(FestivalRole.ADMIN, null, false, true, 0, 20).isEmpty()).isTrue();
        verifyNoInteractions(profiles);
    }

    @ParameterizedTest
    @EnumSource(value = FestivalRole.class, names = {"USER", "BOOTH_MANAGER", "STAFF"})
    void deniedRolesNeverFetchProfiles(FestivalRole role) {
        assertThatThrownBy(() -> directory.list(role, null, null, null, 0, 100)).isInstanceOf(ApiException.class);
        verifyNoInteractions(profiles);
    }

    @Test void staffCannotOpenDirectoryButRetainsSearchScreen() throws Exception {
        mvc.perform(get("/admin/qr/users").cookie(new Cookie("festivalAdminAccess", "STAFF")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/admin/qr").cookie(new Cookie("festivalAdminAccess", "STAFF")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/admin/qr/search")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("전체 회원 조회·필터"))));
        verifyNoInteractions(profiles);
    }

    @ParameterizedTest
    @EnumSource(value = FestivalRole.class, names = {"ADMIN", "SUPER_ADMIN"})
    void adminsCanListMembers(FestivalRole viewer) {
        var member = member(FestivalRole.USER, true, true);
        assertThat(directory.list(viewer, null, null, null, 0, 100).getContent())
                .extracting(QrDtos.QrUserView::userUuid).containsExactly(member.getUserUuid());
    }

    @Test void wristbandBatchPreservesRejoinDetectionAndRevocation() {
        var user = member(FestivalRole.USER, true, false);
        var record = new Wristband(user.getStudentFeeSubjectHash());
        record.issue(UUID.randomUUID(), "이전 계정", UUID.randomUUID(), "담당자", Instant.now());
        wristbands.saveAndFlush(record);
        assertThat(directory.list(FestivalRole.ADMIN, null, null, null, 0, 100).getContent().getFirst().wristband().issued()).isTrue();
        record.revoke(Instant.now()); wristbands.saveAndFlush(record);
        var status = directory.list(FestivalRole.ADMIN, null, null, null, 0, 100).getContent().getFirst().wristband();
        assertThat(status.issued()).isFalse(); assertThat(status.issuedAt()).isNull();
    }

    @Test void htmlRendersFiltersAndPreservesThemAcrossPages() throws Exception {
        for (int i = 0; i < 21; i++) member(FestivalRole.USER, true, true);
        mvc.perform(get("/admin/qr/users").cookie(new Cookie("festivalAdminAccess", "ADMIN"))
                        .param("size", "20").param("verified", "true").param("paid", "true").param("role", "USER"))
                .andExpect(status().isOk()).andExpect(view().name("admin/user-directory"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().string(containsString("전체 회원 조회")))
                .andExpect(content().string(containsString("page=1&amp;size=20&amp;role=USER&amp;verified=true&amp;paid=true")));
        mvc.perform(get("/admin/qr/users").cookie(new Cookie("festivalAdminAccess", "BOOTH_MANAGER")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/admin/qr/users")).andExpect(redirectedUrl("/admin/login"));
    }

    @Test void profileFailureIsNotRenderedAsAnEmptyOrDeletedMemberList() throws Exception {
        member(FestivalRole.USER, false, false);
        when(profiles.getProfiles(anyList())).thenThrow(new SsoException(503, "unavailable"));
        mvc.perform(get("/admin/qr/users").cookie(new Cookie("festivalAdminAccess", "ADMIN")))
                .andExpect(status().isOk()).andExpect(model().attributeDoesNotExist("result"))
                .andExpect(content().string(containsString("SSO 사용자 정보를 조회하지 못했습니다.")));
    }
}
