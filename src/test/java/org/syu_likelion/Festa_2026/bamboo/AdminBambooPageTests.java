package org.syu_likelion.Festa_2026.bamboo;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.syu_likelion.Festa_2026.admin.AdminAccessService;
import org.syu_likelion.Festa_2026.admin.AdminAccessService.AdminIdentity;
import org.syu_likelion.Festa_2026.auth.AuthorizedSsoExecutor.AuthorizedResult;
import org.syu_likelion.Festa_2026.sso.SsoInternalProfileClient;
import org.syu_likelion.Festa_2026.user.FestivalRole;

@SpringBootTest(properties = {
        "sso.client-id=test-client", "sso.client-secret=test-secret",
        "admin.cookie-secure=false",
        "spring.datasource.url=jdbc:h2:mem:bamboo-admin-page;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "bamboo.write-interval=0s",
        "bamboo.writes-per-minute=1000000",
        "bamboo.writes-per-hour=1000000"
})
@AutoConfigureMockMvc
class AdminBambooPageTests {
    private static final UUID OPERATOR = UUID.fromString("123e4567-e89b-12d3-a456-426614174200");
    private static final UUID AUTHOR = UUID.fromString("123e4567-e89b-12d3-a456-426614174201");
    private static final UUID READER = UUID.fromString("123e4567-e89b-12d3-a456-426614174202");

    @Autowired MockMvc mvc;
    @Autowired BambooService bamboo;
    @Autowired BambooRateLimiter rateLimiter;
    @Autowired BambooMessageRepository messages;
    @Autowired BambooNicknameRepository nicknames;
    @Autowired BambooReportRepository reports;
    @Autowired BambooSettingsRepository settings;
    @Autowired BambooModerationAuditRepository moderationAudits;
    @MockitoBean AdminAccessService adminAccess;
    @MockitoBean SsoInternalProfileClient profiles;

    private Long messageId;

    @BeforeEach
    void reset() {
        rateLimiter.clear();
        moderationAudits.deleteAll();
        reports.deleteAll();
        messages.deleteAll();
        nicknames.deleteAll();
        settings.deleteAll();
        bamboo.claimNickname(AUTHOR, "졸린사자42");
        bamboo.claimNickname(READER, "조용한여우07");
        messageId = bamboo.createAs(AUTHOR, "신고될 메시지").id();
        bamboo.reportAs(READER, messageId, BambooReportReason.ABUSE);
    }

    @Test
    void staffSeesLiveChatByDefaultAndCanOpenReportedChat() throws Exception {
        signedInAs(FestivalRole.STAFF);

        mvc.perform(get("/admin/bamboo").cookie(adminCookie()))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/bamboo/list"))
                .andExpect(content().string(containsString("대나무숲 운영")))
                .andExpect(content().string(containsString("실시간 채팅")))
                .andExpect(content().string(containsString("신고된 채팅")))
                .andExpect(content().string(containsString("졸린사자42")));

        mvc.perform(get("/admin/bamboo").param("tab", "REPORTED").cookie(adminCookie()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("졸린사자42")))
                .andExpect(content().string(containsString("신고될 메시지")))
                .andExpect(content().string(containsString("신고 1건")))
                .andExpect(content().string(containsString("ABUSE 1")));
    }

    @Test
    void withoutAnAdminCookieItRedirectsToLogin() throws Exception {
        mvc.perform(get("/admin/bamboo"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/login"));
    }

    @Test
    void postsWithoutCsrfAreRejected() throws Exception {
        signedInAs(FestivalRole.STAFF);

        mvc.perform(post("/admin/bamboo/messages/" + messageId + "/status")
                        .cookie(adminCookie()).param("status", "BLOCKED"))
                .andExpect(status().isForbidden());
    }

    @Test
    void staffCanHideAMessage() throws Exception {
        signedInAs(FestivalRole.STAFF);

        mvc.perform(post("/admin/bamboo/messages/" + messageId + "/status").with(csrf())
                        .cookie(adminCookie()).param("status", "BLOCKED").param("tab", "REPORTED"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/admin/bamboo*"));

        org.assertj.core.api.Assertions.assertThat(
                messages.findById(messageId).orElseThrow().getStatus())
                .isEqualTo(BambooMessageStatus.BLOCKED);
    }

    @Test
    void onlySuperAdminIsOfferedTheAuthorLookup() throws Exception {
        signedInAs(FestivalRole.STAFF);
        mvc.perform(get("/admin/bamboo").cookie(adminCookie()))
                .andExpect(content().string(not(containsString("작성자 확인"))));

        signedInAs(FestivalRole.SUPER_ADMIN);
        mvc.perform(get("/admin/bamboo").cookie(adminCookie()))
                .andExpect(content().string(containsString("작성자 확인")));
    }

    @Test
    void staffCannotChangeOperationSettings() throws Exception {
        signedInAs(FestivalRole.STAFF);

        mvc.perform(post("/admin/bamboo/settings").with(csrf())
                        .cookie(adminCookie()).param("enabled", "false"))
                .andExpect(status().is3xxRedirection());

        org.assertj.core.api.Assertions.assertThat(bamboo.settingsView().enabled()).isTrue();
    }

    @Test
    void adminCanPullTheKillSwitch() throws Exception {
        signedInAs(FestivalRole.ADMIN);

        mvc.perform(post("/admin/bamboo/settings").with(csrf())
                        .cookie(adminCookie()).param("enabled", "false").param("readOnly", "false"))
                .andExpect(status().is3xxRedirection());

        org.assertj.core.api.Assertions.assertThat(bamboo.settingsView().enabled()).isFalse();
    }

    @Test
    void theRecentTabShowsMessagesThatWereNeverReported() throws Exception {
        bamboo.createAs(AUTHOR, "신고되지 않은 메시지");
        signedInAs(FestivalRole.STAFF);

        mvc.perform(get("/admin/bamboo").param("tab", "RECENT").cookie(adminCookie()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("신고되지 않은 메시지")));

        mvc.perform(get("/admin/bamboo").param("tab", "REPORTED").cookie(adminCookie()))
                .andExpect(content().string(not(containsString("신고되지 않은 메시지"))));
    }

    @Test
    void staffCanReadTheLiveCursor() throws Exception {
        signedInAs(FestivalRole.STAFF);

        mvc.perform(get("/admin/bamboo/cursor").cookie(adminCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cursor").isNumber());
    }

    @Test
    void adminCanSearchBlockAndUnblockAnAnonymousParticipantWithAnAuditReason() throws Exception {
        signedInAs(FestivalRole.ADMIN);

        mvc.perform(get("/admin/bamboo").param("participantQuery", "조용한").cookie(adminCookie()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("조용한여우07")))
                .andExpect(content().string(containsString("작성 가능")));

        mvc.perform(post("/admin/bamboo/participants/mute").with(csrf()).cookie(adminCookie())
                        .param("nickname", "조용한여우07").param("minutes", "30")
                        .param("reason", "반복 도배"))
                .andExpect(status().is3xxRedirection());
        org.assertj.core.api.Assertions.assertThat(
                nicknames.findById(READER).orElseThrow().getMutedUntil()).isNotNull();

        mvc.perform(post("/admin/bamboo/participants/mute").with(csrf()).cookie(adminCookie())
                        .param("nickname", "조용한여우07").param("minutes", "0")
                        .param("reason", "이의 제기 확인"))
                .andExpect(status().is3xxRedirection());
        org.assertj.core.api.Assertions.assertThat(
                nicknames.findById(READER).orElseThrow().getMutedUntil()).isNull();
        org.assertj.core.api.Assertions.assertThat(moderationAudits.findAll())
                .hasSize(2)
                .extracting(BambooModerationAudit::getReason)
                .containsExactlyInAnyOrder("반복 도배", "이의 제기 확인");

        mvc.perform(get("/admin/bamboo").cookie(adminCookie()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("참여자 차단·해제 기록")))
                .andExpect(content().string(containsString("반복 도배")))
                .andExpect(content().string(containsString("이의 제기 확인")))
                .andExpect(content().string(containsString("운영자 (ADMIN)")));
    }

    @Test
    void staffCannotSeeOrInvokeParticipantBlocking() throws Exception {
        signedInAs(FestivalRole.STAFF);

        mvc.perform(get("/admin/bamboo").cookie(adminCookie()))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("익명 참여자 검색·차단"))));

        mvc.perform(post("/admin/bamboo/participants/mute").with(csrf()).cookie(adminCookie())
                        .param("nickname", "조용한여우07").param("minutes", "30")
                        .param("reason", "임의 차단 시도"))
                .andExpect(status().is3xxRedirection());
        org.assertj.core.api.Assertions.assertThat(
                nicknames.findById(READER).orElseThrow().getMutedUntil()).isNull();
        org.assertj.core.api.Assertions.assertThat(moderationAudits.count()).isZero();
    }

    private void signedInAs(FestivalRole role) {
        when(adminAccess.authenticate("access-one", null)).thenReturn(new AuthorizedResult<>(
                new AdminIdentity(OPERATOR, "운영자", role), null, null));
    }

    private Cookie adminCookie() {
        return new Cookie("festivalAdminAccess", "access-one");
    }
}
