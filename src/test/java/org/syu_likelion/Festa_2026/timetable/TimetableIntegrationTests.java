package org.syu_likelion.Festa_2026.timetable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import jakarta.servlet.http.Cookie;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.syu_likelion.Festa_2026.admin.AdminAccessService;
import org.syu_likelion.Festa_2026.admin.AdminAccessService.AdminIdentity;
import org.syu_likelion.Festa_2026.auth.AuthorizedSsoExecutor.AuthorizedResult;
import org.syu_likelion.Festa_2026.performance.FestivalPerformanceRepository;
import org.syu_likelion.Festa_2026.performance.PerformanceCategory;
import org.syu_likelion.Festa_2026.performance.PerformanceDtos.PerformanceMutationRequest;
import org.syu_likelion.Festa_2026.performance.PerformanceService;
import org.syu_likelion.Festa_2026.timetable.TimetableDtos.MutationRequest;
import org.syu_likelion.Festa_2026.user.FestivalRole;
import org.syu_likelion.Festa_2026.user.UserDtos.MeResponse;
import org.syu_likelion.Festa_2026.user.UserService;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:timetable-tests;MODE=MySQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class TimetableIntegrationTests {
    private static final Instant NOW = Instant.parse("2026-09-15T09:00:00Z");
    private static final UUID ACTOR = UUID.randomUUID();
    @Autowired MockMvc mvc;
    @Autowired TimetableService timetable;
    @Autowired PerformanceService performances;
    @Autowired FestivalScheduleRepository schedules;
    @Autowired FestivalPerformanceRepository teams;
    @MockitoBean UserService users;
    @MockitoBean AdminAccessService adminAccess;
    @MockitoBean Clock clock;

    @BeforeEach
    void setUp() {
        schedules.deleteAll();
        teams.deleteAll();
        when(clock.instant()).thenReturn(NOW);
        authenticate(FestivalRole.ADMIN);
    }

    @Test
    void publicListAndDetailKeepTimeButHideTitleAndTeamUntilExactPublicationTime() throws Exception {
        Long teamId = team(NOW.minusSeconds(1));
        var item = timetable.createAs(ACTOR, request("비밀팀의 무대", NOW.plusSeconds(1), teamId));
        authenticate(FestivalRole.USER);
        mvc.perform(get("/api/timetable").header("Authorization", "Bearer access"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("X-Access-Token", "rotated"))
                .andExpect(jsonPath("$[0].title").value("TBA"))
                .andExpect(jsonPath("$[0].startsAt").value(NOW.plusSeconds(3600).toString()))
                .andExpect(jsonPath("$[0].endsAt").value(NOW.plusSeconds(7200).toString()))
                .andExpect(jsonPath("$[0].performance").isEmpty())
                .andExpect(jsonPath("$[0].published").value(false));
        mvc.perform(get("/api/timetable/{id}", item.id()).header("Authorization", "Bearer access"))
                .andExpect(jsonPath("$.title").value("TBA")).andExpect(jsonPath("$.performance").isEmpty());
        when(clock.instant()).thenReturn(NOW.plusSeconds(1));
        mvc.perform(get("/api/timetable/{id}", item.id()).header("Authorization", "Bearer access"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.title").value("비밀팀의 무대"))
                .andExpect(jsonPath("$.published").value(true))
                .andExpect(jsonPath("$.performance.id").value(teamId));
    }

    @Test
    void unreleasedPerformanceStaysHiddenAfterSchedulePublicationAndAdminsSeeIt() throws Exception {
        var item = timetable.createAs(ACTOR, request("초청 무대", NOW, team(NOW.plusSeconds(60))));
        mvc.perform(get("/api/timetable/{id}", item.id()).header("Authorization", "Bearer access"))
                .andExpect(jsonPath("$.title").value("초청 무대"))
                .andExpect(jsonPath("$.performance").isEmpty());
        mvc.perform(get("/api/timetable/admin").header("Authorization", "Bearer access"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].performance.teamName").value("비밀팀"));
        timetable.updateAs(item.id(), ACTOR, request("미공개 일정", NOW.plusSeconds(100), null));
        mvc.perform(get("/api/timetable/admin").header("Authorization", "Bearer access"))
                .andExpect(jsonPath("$[0].title").value("미공개 일정"))
                .andExpect(jsonPath("$[0].published").value(false));
    }

    @ParameterizedTest
    @EnumSource(value = FestivalRole.class, names = {"USER", "STAFF", "BOOTH_MANAGER"})
    void nonAdminsCannotManageSchedules(FestivalRole role) throws Exception {
        var item = timetable.createAs(ACTOR, request("개회식", NOW, null));
        authenticate(role);
        mvc.perform(post("/api/timetable").header("Authorization", "Bearer access")
                .contentType(MediaType.APPLICATION_JSON).content(body(NOW, NOW.plusSeconds(1), null)))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/timetable/{id}", item.id()).header("Authorization", "Bearer access")
                .contentType(MediaType.APPLICATION_JSON).content(body(NOW, NOW.plusSeconds(1), null)))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/timetable/{id}", item.id()).header("Authorization", "Bearer access"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/timetable/admin").header("Authorization", "Bearer access"))
                .andExpect(status().isForbidden());
        assertThat(schedules.count()).isEqualTo(1);
    }

    @ParameterizedTest
    @EnumSource(value = FestivalRole.class, names = {"ADMIN", "SUPER_ADMIN"})
    void adminsCanCreateUpdateUnlinkAndDelete(FestivalRole role) throws Exception {
        authenticate(role);
        Long teamId = team(NOW);
        mvc.perform(post("/api/timetable").header("Authorization", "Bearer access")
                .contentType(MediaType.APPLICATION_JSON).content(body(NOW, NOW.plusSeconds(1), teamId)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.performance.id").value(teamId));
        Long id = schedules.findAll().getFirst().getId();
        mvc.perform(patch("/api/timetable/{id}", id).header("Authorization", "Bearer access")
                .contentType(MediaType.APPLICATION_JSON).content(body(NOW, NOW.plusSeconds(2), null)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.performance").isEmpty());
        mvc.perform(delete("/api/timetable/{id}", id).header("Authorization", "Bearer access"))
                .andExpect(status().isNoContent());
        assertThat(teams.existsById(teamId)).isTrue();
        mvc.perform(get("/api/timetable/{id}", id).header("Authorization", "Bearer access"))
                .andExpect(status().isNotFound());
    }

    @Test
    void invalidTimesMissingFieldsAndMissingTeamsAreRejected() throws Exception {
        for (Instant end : List.of(NOW, NOW.minusSeconds(1))) {
            mvc.perform(post("/api/timetable").header("Authorization", "Bearer access")
                    .contentType(MediaType.APPLICATION_JSON).content(body(NOW, end, null)))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(post("/api/timetable").header("Authorization", "Bearer access")
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/timetable").header("Authorization", "Bearer access")
                .contentType(MediaType.APPLICATION_JSON).content(body(NOW, NOW.plusSeconds(1), Long.MAX_VALUE)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/timetable")).andExpect(status().isUnauthorized());
        assertThat(schedules.count()).isZero();
    }

    @Test
    void deletingPerformancePreservesScheduleAndClearsForeignKey() {
        Long teamId = team(NOW);
        var item = timetable.createAs(ACTOR, request("팀의 무대", NOW, teamId));
        performances.deleteAs(teamId);
        var saved = timetable.getAdmin(item.id());
        assertThat(saved.title()).isEqualTo("팀의 무대");
        assertThat(saved.performance()).isNull();
    }

    @Test
    void listIsChronologicalWithStableOrderForTies() {
        var later = timetable.createAs(ACTOR, request("폐회식", NOW, null));
        var first = timetable.createAs(ACTOR, new MutationRequest("개회식", NOW, NOW.plusSeconds(1), NOW, null));
        var second = timetable.createAs(ACTOR, new MutationRequest("안내", NOW, NOW.plusSeconds(2), NOW, null));
        assertThat(timetable.list("access", null).body()).extracting(TimetableDtos.ScheduleResponse::id)
                .containsExactly(first.id(), second.id(), later.id());
    }

    @Test
    void adminPagesRenderAndSaveKoreanTimesWithCsrfProtection() throws Exception {
        var cookie = new Cookie("festivalAdminAccess", "admin");
        when(adminAccess.authenticate("admin", null)).thenReturn(new AuthorizedResult<>(
                new AdminIdentity(ACTOR, "관리자", FestivalRole.ADMIN), null, null));
        Long teamId = team(NOW);
        mvc.perform(get("/admin/timetable/new").cookie(cookie)).andExpect(status().isOk())
                .andExpect(content().string(containsString("비밀팀")));
        mvc.perform(post("/admin/timetable").cookie(cookie).with(csrf())
                .param("title", "개회식").param("startsAt", "2026-09-15T18:00")
                .param("endsAt", "2026-09-15T19:00").param("publishedAt", "2026-09-16T18:00")
                .param("performanceId", teamId.toString()))
                .andExpect(redirectedUrl("/admin/timetable"));
        var saved = timetable.listAll().getFirst();
        assertThat(saved.startsAt()).isEqualTo(NOW);
        mvc.perform(get("/admin/timetable").cookie(cookie)).andExpect(status().isOk())
                .andExpect(content().string(containsString("개회식")));
        mvc.perform(get("/admin/timetable/{id}/edit", saved.id()).cookie(cookie)).andExpect(status().isOk());
        mvc.perform(post("/admin/timetable/{id}", saved.id()).cookie(cookie).with(csrf())
                .param("title", "폐회식").param("startsAt", "2026-09-15T18:00")
                .param("endsAt", "2026-09-15T19:00").param("publishedAt", "2026-09-15T18:00"))
                .andExpect(redirectedUrl("/admin/timetable"));
        assertThat(timetable.getAdmin(saved.id()).performance()).isNull();
        mvc.perform(post("/admin/timetable").cookie(cookie).with(csrf()).param("startsAt", "invalid"))
                .andExpect(status().isOk()).andExpect(view().name("admin/timetable/form"));
        mvc.perform(post("/admin/timetable/{id}/delete", saved.id()).cookie(cookie))
                .andExpect(status().isForbidden());
        mvc.perform(post("/admin/timetable/{id}/delete", saved.id()).cookie(cookie).with(csrf()))
                .andExpect(redirectedUrl("/admin/timetable"));
        assertThat(schedules.count()).isZero();
    }

    @Test
    void staffCannotOpenOrSubmitAdminPages() throws Exception {
        var cookie = new Cookie("festivalAdminAccess", "staff");
        when(adminAccess.authenticate("staff", null)).thenReturn(new AuthorizedResult<>(
                new AdminIdentity(ACTOR, "스태프", FestivalRole.STAFF), null, null));
        mvc.perform(get("/admin/timetable").cookie(cookie)).andExpect(redirectedUrl("/admin"));
        mvc.perform(post("/admin/timetable").cookie(cookie).with(csrf()))
                .andExpect(redirectedUrl("/admin"));
        assertThat(schedules.count()).isZero();
    }

    private void authenticate(FestivalRole role) {
        var me = new MeResponse(ACTOR, "user", "user@example.com", "USER", "ACTIVE",
                null, null, null, null, null, null, null, null, null, Set.of(role));
        when(users.getMe("access", null)).thenReturn(new AuthorizedResult<>(me, "rotated", null));
    }

    private Long team(Instant publishedAt) {
        return performances.createAs(ACTOR, new PerformanceMutationRequest(PerformanceCategory.CLUB,
                "비밀팀", List.of("학생"), NOW, NOW.plusSeconds(100), "소개", List.of(), List.of(),
                List.of(), publishedAt), List.of(), List.of()).id();
    }

    private MutationRequest request(String title, Instant publishedAt, Long teamId) {
        return new MutationRequest(title, NOW.plusSeconds(3600), NOW.plusSeconds(7200), publishedAt, teamId);
    }

    private String body(Instant start, Instant end, Long teamId) {
        return """
                {"title":"개회식", "startsAt":"%s", "endsAt":"%s", "publishedAt":"%s", "performanceId":%s}
                """.formatted(start, end, NOW, teamId);
    }
}
