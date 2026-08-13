package org.syu_likelion.Festa_2026.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
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
import org.syu_likelion.Festa_2026.lostitem.LostItemService;
import org.syu_likelion.Festa_2026.lostitem.LostItemApiService;
import org.syu_likelion.Festa_2026.lostitem.LostItemDtos.LostItemPageResponse;
import org.syu_likelion.Festa_2026.lostitem.LostItemDtos.LostItemResponse;
import org.syu_likelion.Festa_2026.lostitem.LostItemStatus;
import org.syu_likelion.Festa_2026.lostitem.LostItemSort;
import org.syu_likelion.Festa_2026.qr.QrDtos.QrUserView;
import org.syu_likelion.Festa_2026.qr.QrService;
import org.syu_likelion.Festa_2026.user.FestivalRole;

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
    private static final UUID ADMIN_UUID = UUID.fromString("123e4567-e89b-12d3-a456-426614174010");

    @Autowired MockMvc mvc;
    @MockitoBean AuthService auth;
    @MockitoBean AdminAccessService adminAccess;
    @MockitoBean QrService qrService;
    @MockitoBean PerformanceService performanceService;
    @MockitoBean LostItemService lostItemService;
    @MockitoBean LostItemApiService lostItemApiService;
    @MockitoBean SystemMonitoringService systemMonitoringService;

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
                .andExpect(content().string(containsString("QR 사용자 조회")))
                .andExpect(content().string(containsString("서버 장애 긴급 연락처")))
                .andExpect(content().string(containsString("010-4953-5080")))
                .andExpect(content().string(containsString("STAFF")));

        mvc.perform(get("/admin/qr").cookie(new Cookie("festivalAdminAccess", "access-one")))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/qr-scan"))
                .andExpect(content().string(containsString("카메라 시작")));
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
        when(lostItemService.listAdmin(null, LostItemSort.NEWEST)).thenReturn(java.util.List.of());

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
        LostItemResponse notice = new LostItemResponse(7L, "검은색 지갑", "학생회관 앞에서 발견",
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
        when(lostItemService.listAdmin(LostItemStatus.HOLDING, LostItemSort.OLDEST))
                .thenReturn(java.util.List.of());

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
        LostItemResponse notice = new LostItemResponse(7L, "검은색 지갑", "학생회관 앞에서 발견",
                LostItemStatus.HOLDING, "보관 중", false, 0L, java.util.List.of(),
                "축제 스태프", createdAt, createdAt);
        when(lostItemApiService.create(
                org.mockito.ArgumentMatchers.eq("staff-token"),
                org.mockito.ArgumentMatchers.isNull(), any(), any()))
                .thenReturn(new AuthorizedResult<>(notice, null, null));
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
    void onlySuperAdminCanOpenSystemMonitoringPage() throws Exception {
        when(adminAccess.authenticate("super-access", null))
                .thenReturn(new AuthorizedResult<>(identity(FestivalRole.SUPER_ADMIN), null, null));
        when(adminAccess.authenticate("admin-access", null))
                .thenReturn(new AuthorizedResult<>(identity(FestivalRole.ADMIN), null, null));

        mvc.perform(get("/admin/system").cookie(new Cookie("festivalAdminAccess", "super-access")))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/system"))
                .andExpect(content().string(containsString("실시간 시스템 모니터링")));

        mvc.perform(get("/admin/system").cookie(new Cookie("festivalAdminAccess", "admin-access")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin"));

        mvc.perform(get("/admin/system/snapshot")
                        .cookie(new Cookie("festivalAdminAccess", "admin-access")))
                .andExpect(status().isForbidden());
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
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("사용자 UUID"))));
    }

    private AdminIdentity identity(FestivalRole role) {
        return new AdminIdentity(ADMIN_UUID, "축제 관리자", role);
    }

    private QrUserView adminView() {
        return new QrUserView(FestivalRole.ADMIN, null, null, "target@example.com", null, null,
                "구요승", "01012345678", "2024100920", "컴퓨터공학과", 3,
                null, null, null, null, null);
    }
}
