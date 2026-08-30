package org.syu_likelion.Festa_2026.schoolsso;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.util.UriComponentsBuilder;
import org.syu_likelion.Festa_2026.auth.AuthDtos.SignupRequest;
import org.syu_likelion.Festa_2026.auth.AuthDtos.SignupResponse;
import org.syu_likelion.Festa_2026.auth.AuthService;
import org.syu_likelion.Festa_2026.auth.AuthorizedSsoExecutor.AuthorizedResult;
import org.syu_likelion.Festa_2026.user.FestivalUserService;
import org.syu_likelion.Festa_2026.user.FestivalUserService.UserFestivalProfile;
import org.syu_likelion.Festa_2026.user.SchoolVerificationRequestRepository;
import org.syu_likelion.Festa_2026.user.SchoolVerificationStatus;
import org.syu_likelion.Festa_2026.user.UserDtos.MeResponse;
import org.syu_likelion.Festa_2026.user.UserService;

@SpringBootTest(properties = {
        "school-sso.enabled=true",
        "school-sso.client-id=festa-2026",
        "school-sso.client-secret=test-school-secret",
        "school-sso.authorize-url=https://www.syu.ac.kr/festa-sso/authorize",
        "school-sso.callback-url=https://festa.syu-likelion.org/auth/sso/callback",
        "school-sso.issuer=https://www.syu.ac.kr",
        "school-sso.audience=festa-2026",
        "school-sso.return-url=/temporary-auth",
        "school-sso.read-timeout=2s",
        "spring.datasource.url=jdbc:h2:mem:school-sso-tests;MODE=MySQL;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc
class SchoolSsoIntegrationTests {
    private static final RSAKey SIGNING_KEY = key();
    private static final List<RecordedRequest> REQUESTS = new CopyOnWriteArrayList<>();
    private static final HttpServer SCHOOL = server();

    @Autowired MockMvc mvc;
    @Autowired SchoolVerificationRequestRepository verificationRequests;
    @MockitoBean AuthService authService;
    @MockitoBean UserService userService;
    @MockitoBean FestivalUserService festivalUsers;

    @DynamicPropertySource
    static void schoolProperties(DynamicPropertyRegistry registry) {
        registry.add("school-sso.token-url", () -> baseUrl() + "/token");
        registry.add("school-sso.jwks-url", () -> baseUrl() + "/jwks");
    }

    @BeforeEach
    void reset() {
        REQUESTS.clear();
        verificationRequests.deleteAll();
        when(authService.signup(any(), any())).thenReturn(new SignupResponse(
                UUID.fromString("123e4567-e89b-12d3-a456-426614174000")));
    }

    @AfterAll
    static void stop() {
        SCHOOL.stop(0);
    }

    @Test
    void verifiedSchoolProfileOverridesManipulatedSignupFieldsAndIsConsumed() throws Exception {
        MockHttpSession session = authorizeSession();
        String state = stateFromSessionRedirect(session);

        mvc.perform(get("/auth/sso/callback").session(session)
                        .param("state", state).param("code", "valid-one-time-code"))
                .andExpect(redirectedUrl("/temporary-auth?schoolSso=success"));

        mvc.perform(get("/api/auth/school/profile").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("학교홍길동"))
                .andExpect(jsonPath("$.studentNo").value("20260001"))
                .andExpect(jsonPath("$.department").value("컴퓨터공학과"));

        mvc.perform(post("/api/auth/signup").session(session).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"festival01\",\"password\":\"password123\","+
                                "\"email\":\"student@example.com\",\"name\":\"조작이름\","+
                                "\"studentNo\":\"99999999\",\"department\":\"조작학과\","+
                                "\"academicInfoSource\":\"SCHOOL_SSO\"}"))
                .andExpect(status().isOk());

        ArgumentCaptor<SignupRequest> captor = ArgumentCaptor.forClass(SignupRequest.class);
        org.mockito.Mockito.verify(authService).signup(captor.capture(), any());
        assertThat(captor.getValue().name()).isEqualTo("학교홍길동");
        assertThat(captor.getValue().studentNo()).isEqualTo("20260001");
        assertThat(captor.getValue().department()).isEqualTo("컴퓨터공학과");

        mvc.perform(get("/api/auth/school/profile").session(session))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SCHOOL_SSO_VERIFICATION_REQUIRED"));

        assertThat(REQUESTS).anySatisfy(request -> {
            assertThat(request.path()).isEqualTo("/token");
            assertThat(request.authorization()).startsWith("Basic ");
            assertThat(request.body()).contains("code=valid-one-time-code")
                    .contains("redirect_uri=https%3A%2F%2Ffesta.syu-likelion.org%2Fauth%2Fsso%2Fcallback");
        });
    }

    @Test
    void loggedInUserCanCompleteSchoolVerificationAfterSignup() throws Exception {
        UUID userUuid = UUID.fromString("123e4567-e89b-12d3-a456-426614174099");
        MeResponse me = new MeResponse(userUuid, "festival01", "student@example.com", "USER", "ACTIVE",
                "학교홍길동", null, "20260001", "컴퓨터공학과", null, null, null, null, null, null);
        when(userService.getMe(any(), any())).thenReturn(new AuthorizedResult<>(me, null, null));

        MvcResult authorize = mvc.perform(post("/api/users/me/school-verification/authorize")
                        .header("Authorization", "Bearer access-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authorizeUrl").isNotEmpty())
                .andReturn();
        MockHttpSession session = (MockHttpSession) authorize.getRequest().getSession(false);
        String authorizeUrl = tools.jackson.databind.json.JsonMapper.builder().build()
                .readTree(authorize.getResponse().getContentAsString()).get("authorizeUrl").asText();
        String state = UriComponentsBuilder.fromUriString(authorizeUrl).build().getQueryParams().getFirst("state");

        mvc.perform(get("/auth/sso/callback").session(session)
                        .param("state", state).param("code", "valid-one-time-code"))
                .andExpect(redirectedUrl("/temporary-auth?schoolVerification=success"));

        org.mockito.Mockito.verify(festivalUsers).verifySchool(org.mockito.ArgumentMatchers.eq(userUuid), any());
    }

    @Test
    void identityMismatchCreatesPendingSuperAdminApproval() throws Exception {
        UUID userUuid = UUID.fromString("123e4567-e89b-12d3-a456-426614174098");
        MeResponse me = new MeResponse(userUuid, "festival01", "student@example.com", "USER", "ACTIVE",
                "다른이름", null, "2026-OTHER", "컴퓨터공학과", null, null, null, null, null, null);
        when(userService.getMe(any(), any())).thenReturn(new AuthorizedResult<>(me, null, null));

        MvcResult authorize = mvc.perform(post("/api/users/me/school-verification/authorize")
                        .header("Authorization", "Bearer access-token"))
                .andExpect(status().isOk()).andReturn();
        MockHttpSession session = (MockHttpSession) authorize.getRequest().getSession(false);
        String authorizeUrl = tools.jackson.databind.json.JsonMapper.builder().build()
                .readTree(authorize.getResponse().getContentAsString()).get("authorizeUrl").asText();
        String state = UriComponentsBuilder.fromUriString(authorizeUrl).build().getQueryParams().getFirst("state");

        mvc.perform(get("/auth/sso/callback").session(session)
                        .param("state", state).param("code", "valid-one-time-code"))
                .andExpect(redirectedUrl("/temporary-auth?schoolVerification=pending_approval"));

        assertThat(verificationRequests.findByUserUuid(userUuid)).get().satisfies(request -> {
            assertThat(request.getCurrentName()).isEqualTo("다른이름");
            assertThat(request.getSchoolName()).isEqualTo("학교홍길동");
            assertThat(request.getSchoolStudentNo()).isEqualTo("20260001");
        });
        org.mockito.Mockito.verify(festivalUsers, org.mockito.Mockito.never()).verifySchool(any(), any());
    }

    @Test
    void departmentMismatchRequiresConfirmationThenUpdatesAndVerifies() throws Exception {
        UUID userUuid = UUID.fromString("123e4567-e89b-12d3-a456-426614174097");
        MeResponse current = new MeResponse(userUuid, "festival01", "student@example.com", "USER", "ACTIVE",
                "학교홍길동", null, "20260001", "경영학과", null, null, null, null, null, null);
        MeResponse updated = new MeResponse(userUuid, "festival01", "student@example.com", "USER", "ACTIVE",
                "학교홍길동", null, "20260001", "컴퓨터공학과", null, null, null, null, null, null);
        when(userService.getMe(any(), any())).thenReturn(new AuthorizedResult<>(current, null, null));
        when(userService.updateProfile(any(), any(), any())).thenReturn(new AuthorizedResult<>(updated, null, null));
        when(festivalUsers.verifySchool(org.mockito.ArgumentMatchers.eq(userUuid), any())).thenReturn(
                new UserFestivalProfile(java.util.Set.of(), SchoolVerificationStatus.VERIFIED, Instant.now()));

        MvcResult authorize = mvc.perform(post("/api/users/me/school-verification/authorize")
                        .header("Authorization", "Bearer access-token"))
                .andExpect(status().isOk()).andReturn();
        MockHttpSession session = (MockHttpSession) authorize.getRequest().getSession(false);
        String authorizeUrl = tools.jackson.databind.json.JsonMapper.builder().build()
                .readTree(authorize.getResponse().getContentAsString()).get("authorizeUrl").asText();
        String state = UriComponentsBuilder.fromUriString(authorizeUrl).build().getQueryParams().getFirst("state");

        mvc.perform(get("/auth/sso/callback").session(session)
                        .param("state", state).param("code", "valid-one-time-code"))
                .andExpect(redirectedUrl("/temporary-auth?schoolVerification=department_update_required"));
        mvc.perform(get("/api/users/me/school-verification/department").session(session)
                        .header("Authorization", "Bearer access-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentDepartment").value("경영학과"))
                .andExpect(jsonPath("$.schoolDepartment").value("컴퓨터공학과"));
        mvc.perform(post("/api/users/me/school-verification/department/confirm").session(session)
                        .header("Authorization", "Bearer access-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.department").value("컴퓨터공학과"))
                .andExpect(jsonPath("$.schoolVerificationStatus").value("VERIFIED"));

        ArgumentCaptor<org.syu_likelion.Festa_2026.user.UserDtos.ProfileUpdateRequest> update =
                ArgumentCaptor.forClass(org.syu_likelion.Festa_2026.user.UserDtos.ProfileUpdateRequest.class);
        org.mockito.Mockito.verify(userService).updateProfile(any(), any(), update.capture());
        assertThat(update.getValue().department()).isEqualTo("컴퓨터공학과");
    }

    @Test
    void schoolSignupRequiresVerifiedProfile() throws Exception {
        mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"festival01\",\"password\":\"password123\","+
                                "\"email\":\"student@example.com\",\"name\":\"홍길동\","+
                                "\"studentNo\":\"20260001\",\"department\":\"컴퓨터공학과\","+
                                "\"academicInfoSource\":\"SCHOOL_SSO\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SCHOOL_SSO_VERIFICATION_REQUIRED"));
    }

    @Test
    void stateIsRandomAndOneTimeUse() throws Exception {
        MvcResult authorize = mvc.perform(get("/api/auth/school/authorize")).andReturn();
        MockHttpSession session = (MockHttpSession) authorize.getRequest().getSession(false);
        String state = UriComponentsBuilder.fromUriString(authorize.getResponse().getRedirectedUrl())
                .build().getQueryParams().getFirst("state");
        assertThat(state).hasSize(43);

        mvc.perform(get("/auth/sso/callback").session(session)
                        .param("state", "wrong-state").param("code", "code"))
                .andExpect(redirectedUrl("/temporary-auth?schoolSso=invalid_state"));
        mvc.perform(get("/auth/sso/callback").session(session)
                        .param("state", state).param("code", "code"))
                .andExpect(redirectedUrl("/temporary-auth?schoolSso=invalid_state"));
        assertThat(REQUESTS).isEmpty();
    }

    @Test
    void invalidJwtIssuerIsRejectedWithoutExposingProfile() throws Exception {
        MockHttpSession session = authorizeSession();
        String state = stateFromSessionRedirect(session);

        mvc.perform(get("/auth/sso/callback").session(session)
                        .param("state", state).param("code", "invalid-issuer"))
                .andExpect(redirectedUrl("/temporary-auth?schoolSso=failed"));
        mvc.perform(get("/api/auth/school/profile").session(session))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SCHOOL_SSO_VERIFICATION_REQUIRED"));
    }

    private MockHttpSession authorizeSession() throws Exception {
        MvcResult result = mvc.perform(get("/api/auth/school/authorize"))
                .andExpect(status().is3xxRedirection()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private String stateFromSessionRedirect(MockHttpSession session) throws Exception {
        MvcResult result = mvc.perform(get("/api/auth/school/authorize").session(session)).andReturn();
        return UriComponentsBuilder.fromUriString(result.getResponse().getRedirectedUrl())
                .build().getQueryParams().getFirst("state");
    }

    private static RSAKey key() {
        try {
            return new RSAKeyGenerator(2048).keyID("school-key-2026").generate();
        } catch (JOSEException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private static HttpServer server() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/token", SchoolSsoIntegrationTests::token);
            server.createContext("/jwks", SchoolSsoIntegrationTests::jwks);
            server.setExecutor(Executors.newCachedThreadPool());
            server.start();
            return server;
        } catch (IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private static void token(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        REQUESTS.add(new RecordedRequest(exchange.getRequestURI().getPath(), body,
                exchange.getRequestHeaders().getFirst("Authorization")));
        respond(exchange, 200, "{\"access_token\":\"" + jwt(body.contains("code=invalid-issuer"))
                + "\",\"token_type\":\"Bearer\",\"expires_in\":300}");
    }

    private static void jwks(HttpExchange exchange) throws IOException {
        REQUESTS.add(new RecordedRequest(exchange.getRequestURI().getPath(), "",
                exchange.getRequestHeaders().getFirst("Authorization")));
        byte[] publicKey;
        try {
            publicKey = SIGNING_KEY.toRSAPublicKey().getEncoded();
        } catch (JOSEException exception) {
            throw new IOException(exception);
        }
        String pem = "-----BEGIN PUBLIC KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII))
                .encodeToString(publicKey) + "\n-----END PUBLIC KEY-----";
        respond(exchange, 200, "{\"keys\":[{\"kid\":\"school-key-2026\",\"pem\":" + json(pem) + "}]}");
    }

    private static String jwt(boolean invalidIssuer) {
        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(invalidIssuer ? "https://attacker.invalid" : "https://www.syu.ac.kr").audience("festa-2026")
                .subject("20260001").issueTime(Date.from(now)).notBeforeTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(300))).jwtID(UUID.randomUUID().toString())
                .claim("student_id", "20260001").claim("department", "컴퓨터공학과")
                .claim("name", "학교홍길동").claim("consent_target", "2026학년도 총학생회").build();
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256)
                .keyID("school-key-2026").build(), claims);
        try {
            jwt.sign(new RSASSASigner(SIGNING_KEY));
            return jwt.serialize();
        } catch (JOSEException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String json(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r") + "\"";
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static String baseUrl() {
        return "http://127.0.0.1:" + SCHOOL.getAddress().getPort();
    }

    private record RecordedRequest(String path, String body, String authorization) { }
}
