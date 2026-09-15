package org.syu_likelion.Festa_2026.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import jakarta.servlet.http.Cookie;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.syu_likelion.Festa_2026.admin.AdminAccessService.AdminIdentity;
import org.syu_likelion.Festa_2026.auth.AuthDtos.TokenResponse;
import org.syu_likelion.Festa_2026.auth.AuthService;
import org.syu_likelion.Festa_2026.auth.AuthorizedSsoExecutor.AuthorizedResult;
import org.syu_likelion.Festa_2026.error.ApiException;
import org.syu_likelion.Festa_2026.performance.PerformanceService;
import org.syu_likelion.Festa_2026.performance.PerformanceCategory;
import org.syu_likelion.Festa_2026.performance.PerformanceDtos.PerformanceResponse;
import org.syu_likelion.Festa_2026.monitoring.SystemMonitoringService;
import org.syu_likelion.Festa_2026.monitoring.SystemMonitoringService.SystemSnapshot;
import org.syu_likelion.Festa_2026.lostitem.LostItemService;
import org.syu_likelion.Festa_2026.lostitem.LostItemApiService;
import org.syu_likelion.Festa_2026.lostitem.LostItemDtos.LostItemPageResponse;
import org.syu_likelion.Festa_2026.lostitem.LostItemDtos.LostItemResponse;
import org.syu_likelion.Festa_2026.lostitem.LostItemStatus;
import org.syu_likelion.Festa_2026.lostitem.LostItemSort;
import org.syu_likelion.Festa_2026.birthday.BirthdayMessageService;
import org.syu_likelion.Festa_2026.birthday.BirthdayMessageApiService;
import org.syu_likelion.Festa_2026.birthday.BirthdayMessageAdminService;
import org.syu_likelion.Festa_2026.birthday.BirthdayMessageSort;
import org.syu_likelion.Festa_2026.birthday.BirthdayMessageDtos.BirthdayMessagePageResponse;
import org.syu_likelion.Festa_2026.birthday.BirthdayMessageDtos.BirthdayMessageResponse;
import org.syu_likelion.Festa_2026.birthday.BirthdayMessageDtos.PublicAuthor;
import org.syu_likelion.Festa_2026.birthday.BirthdayMessageDtos.AdminBirthdayMessagePageResponse;
import org.syu_likelion.Festa_2026.birthday.BirthdayMessageDtos.AdminBirthdayMessageResponse;
import org.syu_likelion.Festa_2026.birthday.BirthdayMessageDtos.AdminUserView;
import org.syu_likelion.Festa_2026.qr.QrDtos.QrUserView;
import org.syu_likelion.Festa_2026.qr.QrDtos.UserSearchResponse;
import org.syu_likelion.Festa_2026.qr.QrDtos.UserRoleUpdateResponse;
import org.syu_likelion.Festa_2026.qr.QrDtos.UserSchoolVerificationUpdateResponse;
import org.syu_likelion.Festa_2026.qr.QrService;
import org.syu_likelion.Festa_2026.user.FestivalRole;
import org.syu_likelion.Festa_2026.user.SchoolVerificationStatus;
import org.syu_likelion.Festa_2026.user.UserDtos.MeResponse;
import org.syu_likelion.Festa_2026.user.UserService;
import org.syu_likelion.Festa_2026.stamp.StampService;
import org.syu_likelion.Festa_2026.stamp.StampDtos.BoothStampAdminResponse;
import org.syu_likelion.Festa_2026.booth.BoothManagerDirectory;
import org.syu_likelion.Festa_2026.booth.FestivalBooth;
import org.syu_likelion.Festa_2026.poll.PollService;

@SpringBootTest(properties = {
        "sso.client-id=test-client", "sso.client-secret=test-secret",
        "admin.cookie-secure=false",
        "spring.datasource.url=jdbc:h2:mem:admin-pages;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
@AutoConfigureMockMvc
class AdminPageIntegrationTests {
    @Test
    void sidebarKeepsCombinedRolesAndHighlightsDashboard() throws Exception {
        var combined = new AdminIdentity(UUID.randomUUID(), "운영자", FestivalRole.STAFF,
                Set.of(FestivalRole.STAFF, FestivalRole.BOOTH_MANAGER));
        when(adminAccess.authenticate("combined", null)).thenReturn(new AuthorizedResult<>(combined, null, null));
        var response = mvc.perform(get("/admin").cookie(new Cookie("festivalAdminAccess", "combined")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("admin-console.css"))).andReturn().getResponse();
        String html = response.getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        String sidebar = html.substring(html.indexOf("<aside"), html.indexOf("</aside>"));
        assertThat(sidebar).contains("href=\"/admin/stamps\"").doesNotContain("href=\"/admin/sponsors\"");
        assertThat(sidebar).containsPattern("(?s)<a[^>]*href=\"/admin\"[^>]*aria-current=\"page\"");
        assertThat(html).contains("/images/festa.png", "alt=\"Make a Wish\"", "href=\"#admin-content\"", "id=\"admin-content\"")
                .doesNotContain("/images/Logo.webp", "th:replace=");
    }
    @Test
    void dashboardCountAndSidebarMatchAllowedCardsForEveryRoleCombination() throws Exception {
        FestivalRole[] roles = {FestivalRole.BOOTH_MANAGER, FestivalRole.STAFF,
                FestivalRole.ADMIN, FestivalRole.SUPER_ADMIN};
        for (int mask = 1; mask < 16; mask++) {
            Set<FestivalRole> assigned = java.util.EnumSet.noneOf(FestivalRole.class);
            FestivalRole primary = null;
            for (int index = 0; index < roles.length; index++) {
                if ((mask & (1 << index)) != 0) { assigned.add(roles[index]); primary = roles[index]; }
            }
            var actor = new AdminIdentity(UUID.randomUUID(), "운영자", primary, assigned);
            when(adminAccess.authenticate("features", null)).thenReturn(new AuthorizedResult<>(actor, null, null));
            String html = mvc.perform(get("/admin").cookie(new Cookie("festivalAdminAccess", "features")))
                    .andExpect(status().isOk()).andReturn().getResponse()
                    .getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
            Set<String> expected = new java.util.HashSet<>(Set.of("/admin/qr"));
            boolean manager = assigned.contains(FestivalRole.ADMIN) || assigned.contains(FestivalRole.SUPER_ADMIN);
            if (manager) expected.addAll(Set.of("/admin/booths", "/admin/performances", "/admin/polls",
                    "/admin/student-fees", "/admin/sponsors", "/admin/timetable"));
            if (manager || assigned.contains(FestivalRole.STAFF)) expected.addAll(Set.of("/admin/notices",
                    "/admin/lost-items", "/admin/bamboo", "/admin/birthday-messages"));
            if (manager || assigned.contains(FestivalRole.BOOTH_MANAGER)) expected.add("/admin/stamps");
            if (assigned.contains(FestivalRole.SUPER_ADMIN)) expected.addAll(Set.of(
                    "/admin/school-verifications", "/admin/system"));
            String cards = html.substring(html.indexOf("<div class=\"feature-grid\""));
            var cardLinks = java.util.regex.Pattern.compile("<a[^>]*class=\"feature-card\"[^>]*href=\"([^\"]+)\"")
                    .matcher(cards).results().map(match -> match.group(1)).toList();
            assertThat(cardLinks).as("cards for %s", assigned).containsExactlyInAnyOrderElementsOf(expected);
            String sidebar = html.substring(html.indexOf("<aside"), html.indexOf("</aside>"));
            var navigation = java.util.regex.Pattern.compile("<a[^>]*href=\"(/admin/[^\"]+)\"")
                    .matcher(sidebar).results().map(match -> match.group(1)).toList();
            assertThat(navigation).as("navigation for %s", assigned).containsExactlyInAnyOrderElementsOf(expected);
            assertThat(html).contains("class=\"feature-count\">" + cardLinks.size() + "개 기능 사용 가능</span>")
                    .doesNotContain("class=\"admin-header\"", "class=\"admin-stats\"", "class=\"admin-panel\"");
            assertThat(sidebar).contains("<strong>운영자</strong>", "<span>" + primary + "</span>",
                    "method=\"post\"", "action=\"/admin/logout\"", "name=\"_csrf\"", "로그아웃");
        }
    }

    private static final UUID ADMIN_UUID = UUID.fromString("123e4567-e89b-12d3-a456-426614174010");

    @Autowired MockMvc mvc;
    @MockitoBean AuthService auth;
    @MockitoBean AdminAccessService adminAccess;
    @MockitoBean QrService qrService;
    @MockitoBean PerformanceService performanceService;
    @MockitoBean LostItemService lostItemService;
    @MockitoBean LostItemApiService lostItemApiService;
    @MockitoBean BirthdayMessageService birthdayMessageService;
    @MockitoBean BirthdayMessageApiService birthdayMessageApiService;
    @MockitoBean BirthdayMessageAdminService birthdayMessageAdminService;
    @MockitoBean SystemMonitoringService systemMonitoringService;
    @MockitoBean StampService stampService;
    @MockitoBean BoothManagerDirectory boothManagerDirectory;
    @MockitoBean UserService userService;
    @MockitoBean PollService pollService;

    @Test
    void loginPageIsRenderedWithCsrfToken() throws Exception {
        mvc.perform(get("/admin/login"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/login"))
                .andExpect(content().string(containsString("관리자 로그인")))
                .andExpect(content().string(containsString("멋쟁이사자처럼 삼육대학교 14기")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("010-4953-5080"))))
                .andExpect(content().string(containsString("name=\"_csrf\"")));
    }

    @Test
    void loginPostWithoutCsrfIsRejected() throws Exception {
        mvc.perform(post("/admin/login").param("loginId", "admin").param("password", "password123"))
                .andExpect(status().isForbidden());
    }

    @Test
    void authorizedLoginSetsAdminOnlyHttpOnlyCookies() throws Exception {
        when(auth.login(any())).thenReturn(new AuthService.LoginResult(new TokenResponse("access-one"), "refresh-one"));
        when(adminAccess.authenticate("access-one", "refresh-one"))
                .thenReturn(new AuthorizedResult<>(identity(FestivalRole.ADMIN), null, null));

        var result = mvc.perform(post("/admin/login").with(csrf())
                        .param("loginId", "admin").param("password", "password123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin"))
                .andReturn();

        assertThat(result.getResponse().getHeaders("Set-Cookie"))
                .anySatisfy(value -> assertThat(value).contains("festivalAdminAccess=access-one", "HttpOnly", "Path=/admin"))
                .anySatisfy(value -> assertThat(value).contains("festivalAdminRefresh=refresh-one", "HttpOnly", "Path=/admin"));
    }

    @Test
    void dashboardAndQrPageRequireAdminCookie() throws Exception {
        when(adminAccess.authenticate("access-one", null))
                .thenReturn(new AuthorizedResult<>(identity(FestivalRole.STAFF), null, null));

        mvc.perform(get("/admin").cookie(new Cookie("festivalAdminAccess", "access-one")))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/dashboard"))
                .andExpect(content().string(containsString("사용자 및 권한 관리")))
                .andExpect(content().string(containsString("서버 장애 긴급 연락처")))
                .andExpect(content().string(containsString("010-4953-5080")))
                .andExpect(content().string(containsString("대나무숲 운영")))
                .andExpect(content().string(containsString("5개 기능 사용 가능")))
                .andExpect(content().string(containsString("STAFF")));

        mvc.perform(get("/admin/qr").cookie(new Cookie("festivalAdminAccess", "access-one")))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/qr-scan"))
                .andExpect(content().string(containsString("사용자 검색 및 권한 관리")))
                .andExpect(content().string(containsString("카메라 시작")))
                .andExpect(content().string(containsString("이름·학번 등 사용자 정보를 입력")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("토큰 조회"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("사용자 정보 조회</button>"))));
    }

    @Test
    void adminSeesPerformanceManagementAndCanOpenCreatePage() throws Exception {
        when(adminAccess.authenticate("access-one", null))
                .thenReturn(new AuthorizedResult<>(identity(FestivalRole.ADMIN), null, null));

        mvc.perform(get("/admin").cookie(new Cookie("festivalAdminAccess", "access-one")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("공연팀 관리")));

        mvc.perform(get("/admin/performances/new")
                        .cookie(new Cookie("festivalAdminAccess", "access-one")))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/performances/form"))
                .andExpect(content().string(containsString("새 공연팀 등록")))
                .andExpect(content().string(containsString("name=\"_csrf\"")))
                .andExpect(content().string(containsString("name=\"links[0]\"")));
    }

    @Test
    void adminSeesPollManagementAndCanBuildDynamicQuestionForm() throws Exception {
        when(adminAccess.authenticate("access-one", null))
                .thenReturn(new AuthorizedResult<>(identity(FestivalRole.ADMIN), null, null));

        mvc.perform(get("/admin").cookie(new Cookie("festivalAdminAccess", "access-one")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("투표 관리")));

        mvc.perform(get("/admin/polls/new").cookie(new Cookie("festivalAdminAccess", "access-one")))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/polls/form"))
                .andExpect(content().string(containsString("새 투표 만들기")))
                .andExpect(content().string(containsString("questions[0].text")))
                .andExpect(content().string(containsString("questions[0].mediaFiles")))
                .andExpect(content().string(containsString("video/quicktime")))
                .andExpect(content().string(containsString("questions[0].options[0].image")))
                .andExpect(content().string(containsString("name=\"_csrf\"")));
    }

    @Test
    void pollStatusPageRendersAggregateAndAnonymousSubmissionWithoutIdentity() throws Exception {
        when(adminAccess.authenticate("access-one", null))
                .thenReturn(new AuthorizedResult<>(identity(FestivalRole.ADMIN), null, null));
        java.time.Instant now = java.time.Instant.parse("2026-08-18T07:00:00Z");
        var option = new org.syu_likelion.Festa_2026.poll.PollDtos.PollOptionResponse(21L, "공연", null);
        var question = new org.syu_likelion.Festa_2026.poll.PollDtos.PollQuestionResponse(11L,
                "가장 기대되는 프로그램", org.syu_likelion.Festa_2026.poll.PollQuestionType.SINGLE_CHOICE,
                true, java.util.List.of(option), java.util.List.of());
        var poll = new org.syu_likelion.Festa_2026.poll.PollDtos.PollDetailResponse(3L, "축제 사전 설문", "설명",
                true, false, now.minusSeconds(7200), now.minusSeconds(3600), now.plusSeconds(3600),
                now.minusSeconds(600), null, org.syu_likelion.Festa_2026.poll.PollState.OPEN,
                false, 0, true, java.util.List.of(question), now.minusSeconds(7200), now);
        var optionResult = new org.syu_likelion.Festa_2026.poll.PollDtos.OptionResultResponse(21L, "공연", null, 1, 100.0);
        var questionResult = new org.syu_likelion.Festa_2026.poll.PollDtos.QuestionResultResponse(11L,
                "가장 기대되는 프로그램", org.syu_likelion.Festa_2026.poll.PollQuestionType.SINGLE_CHOICE,
                1, java.util.List.of(optionResult), java.util.List.of());
        var result = new org.syu_likelion.Festa_2026.poll.PollDtos.PollResultResponse(3L, "축제 사전 설문", 1,
                now, java.util.List.of(questionResult));
        var answer = new org.syu_likelion.Festa_2026.poll.PollDtos.AdminAnswerResponse(11L,
                "가장 기대되는 프로그램", java.util.List.of(21L), java.util.List.of("공연"), null);
        var submission = new org.syu_likelion.Festa_2026.poll.PollDtos.AdminSubmissionResponse(31L,
                null, null, null, null, now, java.util.List.of(answer));
        when(pollService.adminDetailAs(3L, FestivalRole.ADMIN, 0, 20)).thenReturn(
                new org.syu_likelion.Festa_2026.poll.PollDtos.PollAdminDetailResponse(poll, 1, result,
                        java.util.List.of(submission), 0, 20, 1, 1));

        mvc.perform(get("/admin/polls/3").cookie(new Cookie("festivalAdminAccess", "access-one")))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/polls/detail"))
                .andExpect(content().string(containsString("총 1건 제출")))
                .andExpect(content().string(containsString("익명 응답")))
                .andExpect(content().string(containsString("공연")));
    }

    @Test
    void performanceListWithItemsRendersEditAndDeleteUrls() throws Exception {
        when(adminAccess.authenticate("access-one", null))
                .thenReturn(new AuthorizedResult<>(identity(FestivalRole.ADMIN), null, null));
        java.time.Instant startsAt = java.time.Instant.parse("2026-10-06T10:00:00Z");
        when(performanceService.listAll()).thenReturn(java.util.List.of(new PerformanceResponse(
                17L, PerformanceCategory.CELEBRITY, "연예인", "초청 공연팀",
                java.util.List.of("홍길동"), startsAt, startsAt.plusSeconds(3600),
                "공연 설명", java.util.List.of(), java.util.List.of(), java.util.List.of(),
                startsAt.minusSeconds(86400), true, startsAt.minusSeconds(172800), startsAt.minusSeconds(86400))));

        mvc.perform(get("/admin/performances")
                        .cookie(new Cookie("festivalAdminAccess", "access-one")))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/performances/list"))
                .andExpect(content().string(containsString("/admin/performances/17/edit")))
                .andExpect(content().string(containsString("/admin/performances/17/delete")))
                .andExpect(content().string(containsString("2026-10-06 19:00 KST")));
    }

    @Test
    void legacyFaviconPathRedirectsToSvg() throws Exception {
        mvc.perform(get("/favicon.ico"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/images/favicon.svg"));
    }

    @Test
    void staffCannotOpenPerformanceManagementPage() throws Exception {
        when(adminAccess.authenticate("access-one", null))
                .thenReturn(new AuthorizedResult<>(identity(FestivalRole.STAFF), null, null));

        mvc.perform(get("/admin/performances")
                        .cookie(new Cookie("festivalAdminAccess", "access-one")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin"));
    }

    @Test
    void staffCanOpenLostItemManagementAndBoothManagerCannot() throws Exception {
        when(adminAccess.authenticate("staff-access", null))
                .thenReturn(new AuthorizedResult<>(identity(FestivalRole.STAFF), null, null));
        when(adminAccess.authenticate("booth-access", null))
                .thenReturn(new AuthorizedResult<>(identity(FestivalRole.BOOTH_MANAGER), null, null));
        when(lostItemService.listAdmin(null, LostItemSort.NEWEST, 0, 20))
                .thenReturn(new LostItemPageResponse(java.util.List.of(), 0, 20, 0, 0));

        mvc.perform(get("/admin/lost-items")
                        .cookie(new Cookie("festivalAdminAccess", "staff-access")))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/lost-items/list"))
                .andExpect(content().string(containsString("분실물 공지 관리")));

        mvc.perform(get("/admin/lost-items/new")
                        .cookie(new Cookie("festivalAdminAccess", "staff-access")))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/lost-items/form"))
                .andExpect(content().string(containsString("새 분실물 공지 작성")))
                .andExpect(content().string(containsString("_csrf")))
                .andExpect(content().string(containsString("imageFiles")));
        mvc.perform(get("/admin/lost-items")
                        .cookie(new Cookie("festivalAdminAccess", "booth-access")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin"));
    }

    @Test
    void lostItemPublicApisDoNotRequireLoginAndExposeViewCount() throws Exception {
        java.time.Instant createdAt = java.time.Instant.parse("2026-08-13T03:00:00Z");
        LostItemResponse notice = new LostItemResponse(7L, "검은색 지갑", "학생회관 앞에서 발견", "학생회관",
                LostItemStatus.HOLDING, "보관 중", true, 13L, java.util.List.of(),
                "축제 스태프", createdAt, createdAt);
        when(lostItemService.listPublic(null, LostItemSort.NEWEST, 0, 20))
                .thenReturn(new LostItemPageResponse(java.util.List.of(notice), 0, 20, 1, 1));
        when(lostItemService.getPublic(7L)).thenReturn(notice);

        mvc.perform(get("/api/lost-items"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].title").value("검은색 지갑"))
                .andExpect(jsonPath("$.items[0].viewCount").value(13));

        mvc.perform(get("/api/lost-items/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authorName").value("축제 스태프"))
                .andExpect(jsonPath("$.status").value("HOLDING"));
    }

    @Test
    void lostItemFiltersArePassedToPublicApiAndAdminPage() throws Exception {
        when(lostItemService.listPublic(LostItemStatus.RETURNED, LostItemSort.OLDEST, 0, 20))
                .thenReturn(new LostItemPageResponse(java.util.List.of(), 0, 20, 0, 0));
        when(adminAccess.authenticate("staff-access", null))
                .thenReturn(new AuthorizedResult<>(identity(FestivalRole.STAFF), null, null));
        when(lostItemService.listAdmin(LostItemStatus.HOLDING, LostItemSort.OLDEST, 0, 20))
                .thenReturn(new LostItemPageResponse(java.util.List.of(), 0, 20, 0, 0));

        mvc.perform(get("/api/lost-items")
                        .param("status", "RETURNED").param("sort", "OLDEST"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        mvc.perform(get("/admin/lost-items")
                        .param("status", "HOLDING").param("sort", "OLDEST")
                        .cookie(new Cookie("festivalAdminAccess", "staff-access")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("필터 적용")))
                .andExpect(content().string(containsString("오래된순")));
    }

    @Test
    void lostItemCreateRestApiAcceptsMultipartAndAppearsInOpenApi() throws Exception {
        java.time.Instant createdAt = java.time.Instant.parse("2026-08-13T03:00:00Z");
        LostItemResponse notice = new LostItemResponse(7L, "검은색 지갑", "학생회관 앞에서 발견", "학생회관",
                LostItemStatus.HOLDING, "보관 중", false, 0L, java.util.List.of(),
                "축제 스태프", createdAt, createdAt);
        when(lostItemApiService.create(
                org.mockito.ArgumentMatchers.eq("staff-token"),
                org.mockito.ArgumentMatchers.isNull(), any(), any()))
                .thenReturn(new AuthorizedResult<>(notice, null, null));
        MeResponse staff = mock(MeResponse.class);
        when(staff.festivalRoles()).thenReturn(Set.of(FestivalRole.STAFF));
        when(userService.authenticateEarly(any(),
                org.mockito.ArgumentMatchers.eq("staff-token"),
                org.mockito.ArgumentMatchers.isNull()))
                .thenReturn(new AuthorizedResult<>(staff, null, null));
        MockMultipartFile data = new MockMultipartFile("data", "", MediaType.APPLICATION_JSON_VALUE,
                "{\"title\":\"검은색 지갑\",\"content\":\"학생회관 앞에서 발견\",\"status\":\"HOLDING\",\"pinned\":false}"
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        MockMultipartFile image = new MockMultipartFile("images", "wallet.webp", "image/webp", new byte[]{1});

        mvc.perform(multipart("/api/lost-items").file(data).file(image)
                        .header("Authorization", "Bearer staff-token"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.status").value("HOLDING"));

        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/lost-items'].post").exists())
                .andExpect(jsonPath("$.paths['/api/lost-items/{id}'].patch").exists())
                .andExpect(jsonPath("$.paths['/api/lost-items/{id}'].delete").exists())
                .andExpect(jsonPath("$.paths['/api/lost-items/{id}/status'].patch").exists())
                .andExpect(jsonPath("$.paths['/api/lost-items/{id}/pin'].patch").exists());
    }

    @Test
    void birthdayBoardIsPublicAndCreateAndAdminHeartRoutesAppearInOpenApi() throws Exception {
        java.time.Instant createdAt = java.time.Instant.parse("2026-08-14T03:00:00Z");
        BirthdayMessageResponse message = new BirthdayMessageResponse(11L, "수야 수호 생일 축하해!",
                new PublicAuthor("컴퓨터공학부", "2024******", "홍*동"),
                3L, false, false, createdAt);
        when(birthdayMessageService.list(null, BirthdayMessageSort.LATEST, 0, 30))
                .thenReturn(new BirthdayMessagePageResponse(java.util.List.of(message), 0, 30, 1, 1));
        when(birthdayMessageApiService.create(
                org.mockito.ArgumentMatchers.eq("user-token"),
                org.mockito.ArgumentMatchers.isNull(), any()))
                .thenReturn(new AuthorizedResult<>(message, null, null));

        mvc.perform(get("/api/birthday-messages"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].content").value("수야 수호 생일 축하해!"))
                .andExpect(jsonPath("$.items[0].author.maskedStudentNo").value("2024******"));

        mvc.perform(post("/api/birthday-messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer user-token")
                        .content("{\"content\":\"수야 수호 생일 축하해!\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(11));

        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/birthday-messages'].get").exists())
                .andExpect(jsonPath("$.paths['/api/birthday-messages'].post").exists())
                .andExpect(jsonPath("$.paths['/api/birthday-messages/{id}/heart'].put").exists())
                .andExpect(jsonPath("$.paths['/api/admin/birthday-messages/{id}/hearts'].get").exists())
                .andExpect(jsonPath("$.paths['/api/admin/birthday-messages/{id}'].delete").exists());
    }

    @Test
    void staffCanOpenBirthdayMessageManagementPage() throws Exception {
        when(adminAccess.authenticate("staff-access", null))
                .thenReturn(new AuthorizedResult<>(identity(FestivalRole.STAFF), null, null));
        AdminUserView author = new AdminUserView(FestivalRole.STAFF, null, null, null, null, null,
                "홍길동", null, "2024100920", "컴퓨터공학부", 3,
                null, null, null, null, null);
        java.time.Instant createdAt = java.time.Instant.parse("2026-08-14T03:00:00Z");
        when(birthdayMessageAdminService.listAs(Set.of(FestivalRole.STAFF),
                BirthdayMessageSort.LATEST, 0, 20))
                .thenReturn(new AdminBirthdayMessagePageResponse(java.util.List.of(
                        new AdminBirthdayMessageResponse(11L, "생일 축하해!", 2L, createdAt, author)),
                        0, 20, 1, 1));

        mvc.perform(get("/admin/birthday-messages")
                        .cookie(new Cookie("festivalAdminAccess", "staff-access")))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/birthday-messages/list"))
                .andExpect(content().string(containsString("생일축하 쪽지 관리")))
                .andExpect(content().string(containsString("홍길동")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("2024100920"))))
                .andExpect(content().string(containsString("/admin/birthday-messages/11")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("user@example.com"))));

        when(birthdayMessageAdminService.detailAs(11L, Set.of(FestivalRole.STAFF)))
                .thenReturn(new AdminBirthdayMessageResponse(11L, "생일 축하해!", 2L, createdAt, author));
        mvc.perform(get("/admin/birthday-messages/11")
                        .cookie(new Cookie("festivalAdminAccess", "staff-access")))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/birthday-messages/detail"))
                .andExpect(content().string(containsString("생일축하 쪽지 상세")))
                .andExpect(content().string(containsString("2024100920")))
                .andExpect(content().string(containsString("하트 사용자 2명")));
    }

    @Test
    void onlySuperAdminCanOpenSystemMonitoringPage() throws Exception {
        when(adminAccess.authenticate("super-access", null))
                .thenReturn(new AuthorizedResult<>(identity(FestivalRole.SUPER_ADMIN), null, null));
        when(adminAccess.authenticate("admin-access", null))
                .thenReturn(new AuthorizedResult<>(identity(FestivalRole.ADMIN), null, null));

        mvc.perform(get("/admin").cookie(new Cookie("festivalAdminAccess", "super-access")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("학생 인증 승인")))
                .andExpect(content().string(containsString("0건 대기")));

        mvc.perform(get("/admin").cookie(new Cookie("festivalAdminAccess", "admin-access")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("학생 인증 승인"))));

        mvc.perform(get("/admin/system").cookie(new Cookie("festivalAdminAccess", "super-access")))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/system"))
                .andExpect(content().string(containsString("실시간 시스템 모니터링")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("학생 인증 미승인 요청"))));

        mvc.perform(get("/admin/school-verifications")
                        .cookie(new Cookie("festivalAdminAccess", "super-access")))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/school-verifications"))
                .andExpect(content().string(containsString("학생 인증 미승인 요청")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("CPU · Heap 추이"))));

        mvc.perform(get("/admin/school-verifications")
                        .cookie(new Cookie("festivalAdminAccess", "admin-access")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin"));

        mvc.perform(get("/admin/system").cookie(new Cookie("festivalAdminAccess", "admin-access")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin"));

        mvc.perform(get("/admin/system/snapshot")
                        .cookie(new Cookie("festivalAdminAccess", "admin-access")))
                .andExpect(status().isForbidden());

        when(systemMonitoringService.snapshot()).thenReturn(new SystemSnapshot(
                java.time.Instant.parse("2026-08-30T11:00:00Z"), "HEALTHY",
                null, null, null, null, null, null));
        mvc.perform(get("/admin/system/snapshot")
                        .cookie(new Cookie("festivalAdminAccess", "super-access")))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value("HEALTHY"));
    }

    @Test
    void unauthenticatedDashboardRedirectsToLogin() throws Exception {
        when(adminAccess.authenticate(null, null)).thenThrow(
                new ApiException(HttpStatus.UNAUTHORIZED, "ADMIN_LOGIN_REQUIRED", "로그인이 필요합니다."));

        mvc.perform(get("/admin"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/login"));
    }

    @Test
    void qrScanRendersOnlyServiceFilteredFields() throws Exception {
        when(adminAccess.authenticate("access-one", null))
                .thenReturn(new AuthorizedResult<>(identity(FestivalRole.ADMIN), null, null));
        when(qrService.scanAs(FestivalRole.ADMIN, "qr-token")).thenReturn(adminView());

        mvc.perform(post("/admin/qr/scan").with(csrf())
                        .cookie(new Cookie("festivalAdminAccess", "access-one"))
                        .param("token", "qr-token"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/qr-scan"))
                .andExpect(content().string(containsString("구요승")))
                .andExpect(content().string(containsString("01012345678")))
                .andExpect(content().string(containsString("target@example.com")))
                .andExpect(content().string(containsString("사용자 UUID")))
                .andExpect(content().string(containsString("권한 변경")));
    }

    @Test
    void staffSearchRendersMaskedResultFoundByOriginalQuery() throws Exception {
        when(adminAccess.authenticate("staff-search", null))
                .thenReturn(new AuthorizedResult<>(identity(FestivalRole.STAFF), null, null));
        QrUserView masked = new QrUserView(FestivalRole.STAFF, null, null, null, null, null,
                "홍*동", null, "2024******", "컴퓨터공학부", 3,
                null, null, null, null, null);
        when(qrService.searchAs(FestivalRole.STAFF, "홍길동", 0, 20))
                .thenReturn(new UserSearchResponse(java.util.List.of(masked), 0, 20, 1, 1));

        mvc.perform(post("/admin/qr/search").with(csrf())
                        .cookie(new Cookie("festivalAdminAccess", "staff-search"))
                        .param("query", "홍길동"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/qr-scan"))
                .andExpect(content().string(containsString("1명 검색됨")))
                .andExpect(content().string(containsString("홍*동")))
                .andExpect(content().string(containsString("2024******")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("사용자 UUID"))));
    }

    @Test
    void adminCanChangeSearchedUsersManagementRole() throws Exception {
        UUID target = UUID.fromString("123e4567-e89b-12d3-a456-426614174099");
        when(adminAccess.authenticate("admin-role", null))
                .thenReturn(new AuthorizedResult<>(identity(FestivalRole.ADMIN), null, null));
        QrUserView user = new QrUserView(FestivalRole.ADMIN, target, null, "user@example.com", null, null,
                "홍길동", "01012345678", "2024000001", "컴퓨터공학부", 3,
                null, null, null, null, Set.of(FestivalRole.USER));
        when(qrService.updateRoleAs(ADMIN_UUID, FestivalRole.ADMIN, target, FestivalRole.STAFF))
                .thenReturn(new UserRoleUpdateResponse(target, Set.of(FestivalRole.STAFF)));
        when(qrService.searchAs(FestivalRole.ADMIN, "홍길동", 0, 20))
                .thenReturn(new UserSearchResponse(java.util.List.of(user), 0, 20, 1, 1));

        mvc.perform(post("/admin/qr/users/{id}/role", target).with(csrf())
                        .cookie(new Cookie("festivalAdminAccess", "admin-role"))
                        .param("managementRole", "STAFF").param("query", "홍길동"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/qr-scan"))
                .andExpect(content().string(containsString("사용자 관리 권한을 변경했습니다.")));
        verify(qrService).updateRoleAs(ADMIN_UUID, FestivalRole.ADMIN, target, FestivalRole.STAFF);
    }

    @Test
    void adminCanVerifySearchedUserAsStudent() throws Exception {
        UUID target = UUID.fromString("123e4567-e89b-12d3-a456-426614174099");
        when(adminAccess.authenticate("admin-school", null))
                .thenReturn(new AuthorizedResult<>(identity(FestivalRole.ADMIN), null, null));
        QrUserView user = new QrUserView(FestivalRole.ADMIN, target, null, "user@example.com", null, null,
                "홍길동", "01012345678", "2024000001", "컴퓨터공학부", 3,
                null, null, null, null, Set.of(FestivalRole.USER));
        when(qrService.updateSchoolVerificationAs(FestivalRole.ADMIN, target, true))
                .thenReturn(new UserSchoolVerificationUpdateResponse(target, SchoolVerificationStatus.VERIFIED,
                        true, java.time.Instant.parse("2026-09-03T00:00:00Z")));
        when(qrService.searchAs(FestivalRole.ADMIN, "홍길동", 0, 20))
                .thenReturn(new UserSearchResponse(java.util.List.of(user), 0, 20, 1, 1));

        mvc.perform(post("/admin/qr/users/{id}/school-verification", target).with(csrf())
                        .cookie(new Cookie("festivalAdminAccess", "admin-school"))
                        .param("verified", "true").param("query", "홍길동"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/qr-scan"))
                .andExpect(content().string(containsString("학생 인증을 완료 처리했습니다.")));
        verify(qrService).updateSchoolVerificationAs(FestivalRole.ADMIN, target, true);
    }

    @Test
    void boothManagerStampPageOnlyShowsAssignedBoothsAndQrFlow() throws Exception {
        FestivalBooth booth = mock(FestivalBooth.class);
        when(booth.getId()).thenReturn(7L);
        when(booth.getName()).thenReturn("담당 부스");
        when(booth.getOperator()).thenReturn("운영팀");
        when(adminAccess.authenticate("manager-access", null))
                .thenReturn(new AuthorizedResult<>(identity(FestivalRole.BOOTH_MANAGER), null, null));
        when(stampService.availableBoothsAs(ADMIN_UUID, FestivalRole.BOOTH_MANAGER)).thenReturn(java.util.List.of(booth));
        when(stampService.historyAs(ADMIN_UUID, FestivalRole.BOOTH_MANAGER, 7L, 0, 20))
                .thenReturn(new BoothStampAdminResponse(7L, "담당 부스",
                        java.util.List.of(new org.syu_likelion.Festa_2026.stamp.StampDtos.CurrentStampResponse(
                                null, "홍*동", "2026******", java.time.Instant.parse("2026-08-17T03:00:00Z"),
                                null, org.syu_likelion.Festa_2026.stamp.StampMethod.QR)), java.util.List.of()));

        mvc.perform(get("/admin/stamps").cookie(new Cookie("festivalAdminAccess", "manager-access")))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/stamps"))
                .andExpect(content().string(containsString("담당 부스")))
                .andExpect(content().string(containsString("사용자 정보가 자동으로 조회됩니다")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("QR 사용자 확인"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("QR 토큰으로 조회"))))
                .andExpect(content().string(containsString("마스킹된 기록")))
                .andExpect(content().string(containsString("홍*동")))
                .andExpect(content().string(containsString("2026******")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("사용자 검색 임의 처리"))));
    }

    @Test
    void adminStampPageShowsEveryBoothSearchAndHistory() throws Exception {
        FestivalBooth booth = mock(FestivalBooth.class);
        when(booth.getId()).thenReturn(8L);
        when(booth.getName()).thenReturn("전체 관리 부스");
        when(booth.getOperator()).thenReturn("운영팀");
        FestivalBooth otherBooth = mock(FestivalBooth.class);
        when(otherBooth.getId()).thenReturn(9L);
        when(otherBooth.getName()).thenReturn("다른 부스");
        when(otherBooth.getOperator()).thenReturn("다른 운영팀");
        AdminIdentity adminAndManager = new AdminIdentity(ADMIN_UUID, "축제 관리자", FestivalRole.ADMIN,
                Set.of(FestivalRole.ADMIN, FestivalRole.BOOTH_MANAGER, FestivalRole.USER));
        when(adminAccess.authenticate("admin-stamp-access", null))
                .thenReturn(new AuthorizedResult<>(adminAndManager, null, null));
        when(stampService.availableBoothsAs(ADMIN_UUID, FestivalRole.ADMIN))
                .thenReturn(java.util.List.of(booth, otherBooth));
        when(stampService.historyAs(ADMIN_UUID, FestivalRole.ADMIN, 8L, 0, 20))
                .thenReturn(new BoothStampAdminResponse(8L, "전체 관리 부스",
                        java.util.List.of(new org.syu_likelion.Festa_2026.stamp.StampDtos.CurrentStampResponse(
                                ADMIN_UUID, "스탬프 사용자", "2026000001", java.time.Instant.parse("2026-08-17T03:00:00Z"),
                                ADMIN_UUID, org.syu_likelion.Festa_2026.stamp.StampMethod.QR)),
                        java.util.List.of(new org.syu_likelion.Festa_2026.stamp.StampDtos.StampHistoryResponse(
                                1L, org.syu_likelion.Festa_2026.stamp.StampAction.GRANT,
                                org.syu_likelion.Festa_2026.stamp.StampMethod.QR, ADMIN_UUID, "스탬프 사용자",
                                ADMIN_UUID, "축제 관리자", java.time.Instant.parse("2026-08-17T03:00:00Z"))),
                        0, 20, 31, 2));
        QrUserView searchUser = adminView();
        when(qrService.searchAs(FestivalRole.ADMIN, "컴퓨터공학과", 0, 20))
                .thenReturn(new UserSearchResponse(java.util.List.of(searchUser), 0, 20, 21, 2));

        mvc.perform(get("/admin/stamps").cookie(new Cookie("festivalAdminAccess", "admin-stamp-access"))
                        .param("userQuery", "컴퓨터공학과"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("전체 관리 부스")))
                .andExpect(content().string(containsString("다른 부스")))
                .andExpect(content().string(containsString("사용자 검색 임의 처리")))
                .andExpect(content().string(containsString("스탬프 사용자 검색 결과 페이지")))
                .andExpect(content().string(containsString("userPage=1")))
                .andExpect(content().string(containsString("지급·회수 감사 이력")))
                .andExpect(content().string(containsString("감사 이력 페이지")))
                .andExpect(content().string(containsString("page=1")))
                .andExpect(content().string(containsString("2026-08-17 12:00:00 KST")));
    }

    @Test
    void staffCannotOpenStampPage() throws Exception {
        when(adminAccess.authenticate("staff-stamp-access", null))
                .thenReturn(new AuthorizedResult<>(identity(FestivalRole.STAFF), null, null));
        mvc.perform(get("/admin/stamps").cookie(new Cookie("festivalAdminAccess", "staff-stamp-access")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin"));
    }

    private AdminIdentity identity(FestivalRole role) {
        return new AdminIdentity(ADMIN_UUID, "축제 관리자", role);
    }

    private QrUserView adminView() {
        return new QrUserView(FestivalRole.ADMIN,
                UUID.fromString("123e4567-e89b-12d3-a456-426614174099"), null, "target@example.com", null, null,
                "구요승", "01012345678", "2024100920", "컴퓨터공학과", 3,
                null, null, null, null, Set.of(FestivalRole.USER));
    }
}
