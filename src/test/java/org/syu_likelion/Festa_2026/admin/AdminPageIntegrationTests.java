package org.syu_likelion.Festa_2026.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.syu_likelion.Festa_2026.admin.AdminAccessService.AdminIdentity;
import org.syu_likelion.Festa_2026.auth.AuthDtos.TokenResponse;
import org.syu_likelion.Festa_2026.auth.AuthService;
import org.syu_likelion.Festa_2026.auth.AuthorizedSsoExecutor.AuthorizedResult;
import org.syu_likelion.Festa_2026.error.ApiException;
import org.syu_likelion.Festa_2026.performance.PerformanceService;
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

    @Test
    void loginPageIsRenderedWithCsrfToken() throws Exception {
        mvc.perform(get("/admin/login"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/login"))
                .andExpect(content().string(containsString("관리자 로그인")))
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
    void staffCannotOpenPerformanceManagementPage() throws Exception {
        when(adminAccess.authenticate("access-one", null))
                .thenReturn(new AuthorizedResult<>(identity(FestivalRole.STAFF), null, null));

        mvc.perform(get("/admin/performances")
                        .cookie(new Cookie("festivalAdminAccess", "access-one")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin"));
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
