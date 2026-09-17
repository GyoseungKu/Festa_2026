package org.syu_likelion.Festa_2026.config;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.syu_likelion.Festa_2026.admin.AdminAccessService;
import org.syu_likelion.Festa_2026.auth.AuthorizedSsoExecutor.AuthorizedResult;
import org.syu_likelion.Festa_2026.error.ApiException;
import org.syu_likelion.Festa_2026.sso.SsoException;
import org.syu_likelion.Festa_2026.user.FestivalRole;

@SpringBootTest
@AutoConfigureMockMvc
class SwaggerAccessIntegrationTests {
    @Autowired MockMvc mvc;
    @MockitoBean AdminAccessService admins;

    @ParameterizedTest
    @ValueSource(strings = {"/admin/v3/api-docs", "/admin/v3/api-docs.yaml", "/admin/v3/api-docs/swagger-config",
            "/admin/swagger-ui/swagger-initializer.js", "/admin/swagger-ui/swagger-ui.css"})
    void anonymousCannotReadSpecificationsOrAssets(String path) throws Exception {
        when(admins.authenticateForSwagger(null, null)).thenThrow(new ApiException(HttpStatus.UNAUTHORIZED, "ADMIN_LOGIN_REQUIRED", "login"));
        mvc.perform(get(path)).andExpect(status().isUnauthorized()).andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test void anonymousUiRedirectsToLogin() throws Exception {
        when(admins.authenticateForSwagger(null, null)).thenThrow(new ApiException(HttpStatus.UNAUTHORIZED, "ADMIN_LOGIN_REQUIRED", "login"));
        mvc.perform(get("/admin/swagger-ui.html")).andExpect(redirectedUrl("/admin/login?next=swagger"));
        mvc.perform(get("/admin/swagger-ui/index.html")).andExpect(redirectedUrl("/admin/login?next=swagger"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "SUPER_ADMIN"})
    void allowedRolesCanLoadUiConfigurationAndBothSpecificationFormats(String role) throws Exception {
        allow(FestivalRole.valueOf(role));
        mvc.perform(get("/admin/swagger-ui.html").cookie(access())).andExpect(redirectedUrl("/admin/swagger-ui/index.html"));
        mvc.perform(get("/admin/swagger-ui/index.html").cookie(access())).andExpect(status().isOk());
        mvc.perform(get("/admin/swagger-ui/swagger-initializer.js").cookie(access())).andExpect(status().isOk());
        mvc.perform(get("/admin/v3/api-docs/swagger-config").cookie(access()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.url").value("/admin/v3/api-docs"));
        mvc.perform(get("/admin/v3/api-docs").cookie(access())).andExpect(status().isOk());
        mvc.perform(get("/admin/v3/api-docs.yaml").cookie(access())).andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/admin/swagger-ui.html", "/admin/swagger-ui/index.html",
            "/admin/swagger-ui/swagger-initializer.js", "/admin/swagger-ui/swagger-ui.css",
            "/admin/v3/api-docs", "/admin/v3/api-docs.yaml", "/admin/v3/api-docs/swagger-config"})
    void insufficientRoleCannotReadUiSpecificationsOrAssets(String path) throws Exception {
        when(admins.authenticateForSwagger("docs", null)).thenThrow(
                new ApiException(HttpStatus.FORBIDDEN, "SWAGGER_ROLE_REQUIRED", "forbidden"));
        mvc.perform(get(path).cookie(access())).andExpect(status().isForbidden())
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test void revokedAuthenticationIsCheckedAgainAndCookiesAreCleared() throws Exception {
        allow(FestivalRole.ADMIN);
        mvc.perform(get("/admin/v3/api-docs").cookie(access())).andExpect(status().isOk());
        when(admins.authenticateForSwagger("docs", null)).thenThrow(new SsoException(401, "revoked"));
        mvc.perform(get("/admin/v3/api-docs").cookie(access())).andExpect(status().isUnauthorized())
                .andExpect(cookie().maxAge("festivalAdminAccess", 0)).andExpect(cookie().maxAge("festivalAdminRefresh", 0));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/v3/api-docs", "/v3/api-docs.yaml", "/v3/api-docs/swagger-config", "/webjars/swagger-ui/index.html"})
    void oldDirectPathsDoNotExposeDocumentation(String path) throws Exception {
        mvc.perform(get(path)).andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/swagger-ui.html", "/swagger-ui/index.html"})
    void legacyUiLinksRedirectThroughProtectedEntry(String path) throws Exception {
        mvc.perform(get(path).param("url", "https://example.org/ignored.json"))
                .andExpect(redirectedUrl("/admin/swagger-ui.html"))
                .andExpect(header().string("Cache-Control", "no-store"));
        when(admins.authenticateForSwagger(null, null)).thenThrow(new ApiException(HttpStatus.UNAUTHORIZED, "ADMIN_LOGIN_REQUIRED", "login"));
        mvc.perform(get("/admin/swagger-ui.html")).andExpect(redirectedUrl("/admin/login?next=swagger"));
    }

    private Cookie access() { return new Cookie("festivalAdminAccess", "docs"); }
    private void allow(FestivalRole role) {
        when(admins.authenticateForSwagger("docs", null)).thenReturn(new AuthorizedResult<>(
                new AdminAccessService.AdminIdentity(UUID.randomUUID(), "admin", role), null, null));
    }
}
