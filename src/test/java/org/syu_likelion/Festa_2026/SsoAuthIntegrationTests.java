package org.syu_likelion.Festa_2026;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.syu_likelion.Festa_2026.user.FestivalUserRepository;
import org.syu_likelion.Festa_2026.sso.SsoAuthClient;
import org.syu_likelion.Festa_2026.sso.SsoInternalProfileClient;

@SpringBootTest(properties = {
        "sso.client-id=test-client", "sso.client-secret=test-secret",
        "sso.read-timeout=100ms", "auth.refresh-cookie-secure=false",
        "spring.datasource.url=jdbc:h2:mem:sso-tests;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
@AutoConfigureMockMvc
class SsoAuthIntegrationTests {
    private static final BlockingQueue<StubResponse> RESPONSES = new LinkedBlockingQueue<>();
    private static final List<RecordedRequest> REQUESTS = new CopyOnWriteArrayList<>();
    private static final HttpServer SSO = startServer();
    private static final String UUID = "123e4567-e89b-12d3-a456-426614174000";

    @Autowired MockMvc mvc;
    @Autowired FestivalUserRepository users;
    @Autowired JdbcTemplate jdbc;
    @Autowired SsoInternalProfileClient internalProfiles;

    @DynamicPropertySource
    static void ssoProperties(DynamicPropertyRegistry registry) {
        registry.add("sso.base-url", () -> "http://127.0.0.1:" + SSO.getAddress().getPort());
    }

    @BeforeEach
    void reset() {
        RESPONSES.clear();
        REQUESTS.clear();
        users.deleteAll();
    }

    @AfterAll
    static void stopServer() { SSO.stop(0); }

    @Test
    void signupRequiresSsoEmailVerification() throws Exception {
        enqueue(400, "{}");
        mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"festival01\",\"password\":\"Password123!\","
                                + "\"email\":\"student@example.com\",\"name\":\"홍길동\","
                                + "\"studentNo\":\"20260001\",\"department\":\"컴퓨터공학과\"}"))
                .andExpect(status().isBadRequest());

        enqueue(204, "");
        mvc.perform(post("/api/auth/signup/email/verify").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"student@example.com\",\"code\":\"123456\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void signupRejectsMissingRequiredProfileBeforeCallingSso() throws Exception {
        mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"festival01\",\"password\":\"Password123!\","
                                + "\"email\":\"student@example.com\",\"phone\":\"01012345678\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message", containsString("name")))
                .andExpect(jsonPath("$.message", containsString("studentNo")))
                .andExpect(jsonPath("$.message", containsString("department")));
        org.assertj.core.api.Assertions.assertThat(REQUESTS).isEmpty();
    }

    @Test
    void signupAvailabilityChecksAreValidatedAndForwardedWithClientAuthentication() throws Exception {
        enqueue(200, "{\"available\":true}");
        mvc.perform(get("/api/auth/check/login-id").param("loginId", "festival01"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.available").value(true));
        enqueue(200, "{\"available\":true}");
        mvc.perform(get("/api/auth/check/email").param("email", "student@example.com"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.available").value(true));
        enqueue(200, "{\"available\":false}");
        mvc.perform(get("/api/auth/check/student-no").param("studentNo", "20260001"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.available").value(false));
        enqueue(200, "{\"available\":true}");
        mvc.perform(get("/api/auth/check/phone").param("phone", "01012345678"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.available").value(true));

        org.assertj.core.api.Assertions.assertThat(REQUESTS).extracting(RecordedRequest::path)
                .containsExactly("/api/auth/check/login-id", "/api/auth/check/email",
                        "/api/auth/check/student-no", "/api/auth/check/phone");
        org.assertj.core.api.Assertions.assertThat(REQUESTS).extracting(RecordedRequest::query)
                .containsExactly("value=festival01", "value=student%40example.com",
                        "value=20260001", "value=01012345678");
        org.assertj.core.api.Assertions.assertThat(REQUESTS)
                .allSatisfy(request -> org.assertj.core.api.Assertions.assertThat(request.authorization())
                        .startsWith("Basic "));

        mvc.perform(get("/api/auth/check/phone").param("phone", "010-1234-5678"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mvc.perform(get("/api/auth/check/email").param("email", "not-an-email"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void signupSuccessLinksOnlyUserUuid() throws Exception {
        enqueue(201, "{\"userUuid\":\"" + UUID + "\"}");
        mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"festival01\",\"password\":\"Password123!\","
                                + "\"email\":\"student@example.com\",\"name\":\"홍길동\","
                                + "\"studentNo\":\"20260001\",\"department\":\"컴퓨터공학과\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.userUuid").value(UUID));
        org.assertj.core.api.Assertions.assertThat(users.findAll()).singleElement()
                .satisfies(user -> org.assertj.core.api.Assertions.assertThat(user.getUserUuid().toString()).isEqualTo(UUID));
        org.assertj.core.api.Assertions.assertThat(
                jdbc.queryForObject("select user_uuid from festival_users", String.class)).isEqualTo(UUID);
    }

    @Test
    void signupAcceptsAndForwardsAllOptionalUserFields() throws Exception {
        enqueue(201, "{\"userUuid\":\"" + UUID + "\"}");
        mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"festival01\",\"password\":\"Password123!\","
                                + "\"email\":\"student@example.com\",\"name\":\"홍길동\","
                                + "\"phone\":\"01012345678\",\"studentNo\":\"20260001\","
                                + "\"department\":\"컴퓨터공학과\",\"grade\":2,"
                                + "\"enrollment\":\"ENROLLED\",\"birthDate\":\"2005-01-02\"}"))
                .andExpect(status().isOk());

        org.assertj.core.api.Assertions.assertThat(REQUESTS.getFirst().body())
                .contains("\"name\":\"홍길동\"", "\"phone\":\"01012345678\"",
                        "\"studentNo\":\"20260001\"", "\"department\":\"컴퓨터공학과\"",
                        "\"grade\":2", "\"enrollment\":\"ENROLLED\"", "\"birthDate\":\"2005-01-02\"");
    }

    @Test
    void loginReturnsAccessTokenAndHttpOnlyRefreshCookie() throws Exception {
        enqueue(200, "{\"accessToken\":\"access-one\"}", "refreshToken=refresh-one; HttpOnly; Secure; Path=/");
        enqueue(200, meJson("USER"));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"festival01\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-one"))
                .andExpect(header().string("Set-Cookie", containsString("festivalRefreshToken=refresh-one")))
                .andExpect(header().string("Set-Cookie", containsString("HttpOnly")));
    }

    @Test
    void loginMapsInvalidAndForbiddenAccounts() throws Exception {
        enqueue(401, "{}");
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"bad-user\",\"password\":\"password123\"}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        enqueue(403, "{}");
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"blocked\",\"password\":\"password123\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCOUNT_FORBIDDEN"));
    }

    @Test
    void findIdUsesEmailVerificationWithoutBearerAuthentication() throws Exception {
        enqueue(200, "{\"ok\":true,\"message\":\"sent\"}");
        mvc.perform(post("/api/auth/email/send").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"find@example.com\",\"purpose\":\"FIND_ID\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(containsString("인증번호")));

        enqueue(200, "{\"loginId\":\"festival01\"}");
        mvc.perform(post("/api/auth/email/find-id/verify").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"find@example.com\",\"code\":\"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loginId").value("festival01"));

        org.assertj.core.api.Assertions.assertThat(REQUESTS).extracting(RecordedRequest::path)
                .containsExactly("/api/auth/email/send", "/api/auth/email/find-id/verify");
        org.assertj.core.api.Assertions.assertThat(REQUESTS.getFirst().body())
                .contains("\"purpose\":\"FIND_ID\"");
        org.assertj.core.api.Assertions.assertThat(REQUESTS.getFirst().authorization())
                .startsWith("Basic ");
    }

    @Test
    void passwordResetSendsLoginIdAndAppliesCodeAndPasswordInOneRequest() throws Exception {
        enqueue(200, "{\"ok\":true,\"message\":\"sent\"}");
        mvc.perform(post("/api/auth/email/send").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"festival01\",\"email\":\"reset@example.com\","
                                + "\"purpose\":\"RESET_PASSWORD\"}"))
                .andExpect(status().isOk());

        enqueue(200, "{\"ok\":true,\"message\":\"reset\"}");
        mvc.perform(post("/api/auth/email/reset-password/verify")
                        .cookie(new jakarta.servlet.http.Cookie("festivalRefreshToken", "old-refresh"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"festival01\",\"email\":\"reset@example.com\","
                                + "\"code\":\"123456\",\"newPassword\":\"NewPassword123!\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(containsString("재설정")))
                .andExpect(header().string("Set-Cookie", containsString("Max-Age=0")));

        org.assertj.core.api.Assertions.assertThat(REQUESTS).extracting(RecordedRequest::path)
                .containsExactly("/api/auth/email/send", "/api/auth/email/reset-password/verify");
        org.assertj.core.api.Assertions.assertThat(REQUESTS.get(1).body())
                .contains("\"loginId\":\"festival01\"", "\"code\":\"123456\"",
                        "\"newPassword\":\"NewPassword123!\"");
    }

    @Test
    void recoverySendHidesAccountExistenceAndValidatesResetInputLocally() throws Exception {
        enqueue(404, "{\"code\":\"USER_NOT_FOUND\",\"message\":\"user not found\"}");
        mvc.perform(post("/api/auth/email/send").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"missing@example.com\",\"purpose\":\"FIND_ID\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(containsString("계정이 있다면")));

        mvc.perform(post("/api/auth/email/send").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"reset@example.com\",\"purpose\":\"RESET_PASSWORD\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LOGIN_ID_REQUIRED"));

        mvc.perform(post("/api/auth/email/send").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"reset@example.com\",\"purpose\":\"SIGNUP\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        mvc.perform(post("/api/auth/email/reset-password/verify").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"festival01\",\"email\":\"reset@example.com\","
                                + "\"code\":\"123\",\"newPassword\":\"short\"}"))
                .andExpect(status().isBadRequest());
        org.assertj.core.api.Assertions.assertThat(REQUESTS).hasSize(1);
    }

    @Test
    void meAllowsNullStudentFieldsAndSeparatesFestivalRole() throws Exception {
        enqueue(200, meJson("ADMIN"));
        mvc.perform(get("/api/users/me").header("Authorization", "Bearer access-one"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(nullValue()))
                .andExpect(jsonPath("$.studentNo").value(nullValue()))
                .andExpect(jsonPath("$.department").value(nullValue()))
                .andExpect(jsonPath("$.grade").value(nullValue()))
                .andExpect(jsonPath("$.enrollment").value(nullValue()))
                .andExpect(jsonPath("$.birthDate").value(nullValue()))
                .andExpect(jsonPath("$.ssoRole").value("ADMIN"))
                .andExpect(jsonPath("$.festivalRoles", hasItem("USER")))
                .andExpect(jsonPath("$.festivalRoles.length()").value(1));
    }

    @Test
    void expiredAccessAutomaticallyRefreshesAndAppliesRotation() throws Exception {
        enqueue(401, "{}");
        enqueue(200, "{\"accessToken\":\"access-two\"}", "refreshToken=refresh-two; HttpOnly; Path=/");
        enqueue(200, meJson("USER"));
        mvc.perform(get("/api/users/me").header("Authorization", "Bearer expired")
                        .cookie(new jakarta.servlet.http.Cookie("festivalRefreshToken", "refresh-one")))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Access-Token", "access-two"))
                .andExpect(header().string("Set-Cookie", containsString("festivalRefreshToken=refresh-two")));
        org.assertj.core.api.Assertions.assertThat(REQUESTS.get(1).cookie()).contains("refreshToken=refresh-one");
        org.assertj.core.api.Assertions.assertThat(REQUESTS.get(2).authorization()).isEqualTo("Bearer access-two");
    }

    @Test
    void logoutAlwaysClearsLocalRefreshCookieEvenWhenSsoFails() throws Exception {
        enqueue(503, "{}");
        mvc.perform(post("/api/auth/logout").header("Authorization", "Bearer access-one"))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Set-Cookie", containsString("Max-Age=0")));
    }

    @Test
    void logoutLeavesNoFestivalSessionAndRevokedAccessIsRejected() throws Exception {
        enqueue(204, "");
        mvc.perform(post("/api/auth/logout").header("Authorization", "Bearer access-one"))
                .andExpect(status().isNoContent());
        enqueue(401, "{}");
        mvc.perform(get("/api/users/me").header("Authorization", "Bearer access-one"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Set-Cookie", containsString("Max-Age=0")));
    }

    @Test
    void emailChangeUsesDedicatedThreeStepFlow() throws Exception {
        enqueue(204, ""); enqueue(204, ""); enqueue(204, "");
        mvc.perform(post("/api/users/me/email/verification").header("Authorization", "Bearer access-one")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"new@example.com\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/users/me/email/verification/confirm").header("Authorization", "Bearer access-one")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"new@example.com\",\"code\":\"123456\"}"))
                .andExpect(status().isOk());
        mvc.perform(patch("/api/users/me/email").header("Authorization", "Bearer access-one")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"new@example.com\"}"))
                .andExpect(status().isOk());
        org.assertj.core.api.Assertions.assertThat(REQUESTS).extracting(RecordedRequest::path).containsExactly(
                "/api/users/me/email/verification", "/api/users/me/email/verification/confirm", "/api/users/me/email");
    }

    @Test
    void profileUpdateAllowsOnlyUserEditableSsoFieldsAndReturnsRefreshedProfile() throws Exception {
        enqueue(200, "{\"success\":true}");
        enqueue(200, "{\"userUuid\":\"" + UUID + "\",\"loginId\":\"festival01\"," +
                "\"email\":\"student@example.com\",\"ssoRole\":\"USER\",\"status\":\"ACTIVE\"," +
                "\"name\":\"홍길동\",\"phone\":\"01012345678\",\"studentNo\":\"20260001\"," +
                "\"department\":\"소프트웨어학과\",\"grade\":3,\"enrollment\":\"ENROLLED\"}");

        mvc.perform(patch("/api/users/me/profile").header("Authorization", "Bearer access-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"01012345678\",\"department\":\"소프트웨어학과\"," +
                                "\"grade\":3,\"enrollment\":\"ENROLLED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").value("01012345678"))
                .andExpect(jsonPath("$.department").value("소프트웨어학과"))
                .andExpect(jsonPath("$.grade").value(3))
                .andExpect(jsonPath("$.enrollment").value("ENROLLED"));

        org.assertj.core.api.Assertions.assertThat(REQUESTS.getFirst().path()).isEqualTo("/api/users/me/profile");
        org.assertj.core.api.Assertions.assertThat(REQUESTS.getFirst().body())
                .contains("\"phone\":\"01012345678\"", "\"department\":\"소프트웨어학과\"",
                        "\"grade\":3", "\"enrollment\":\"ENROLLED\"")
                .doesNotContain("\"name\"", "\"studentNo\"", "\"birthDate\"");
    }

    @Test
    void profileUpdateRejectsEmptyOrInvalidEditableFieldsBeforeSsoCall() throws Exception {
        mvc.perform(patch("/api/users/me/profile").header("Authorization", "Bearer access-token")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PROFILE_UPDATE_REQUIRED"));
        mvc.perform(patch("/api/users/me/profile").header("Authorization", "Bearer access-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"010-1234-5678\",\"grade\":7}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        org.assertj.core.api.Assertions.assertThat(REQUESTS).isEmpty();
    }

    @Test
    void passwordFailureIsMappedAndSuccessClearsTokens() throws Exception {
        String body = "{\"currentPassword\":\"wrong-password\",\"newPassword\":\"new-password123\"}";
        enqueue(400, "{}");
        mvc.perform(patch("/api/users/me/password").header("Authorization", "Bearer access-one")
                        .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        enqueue(204, "");
        mvc.perform(patch("/api/users/me/password").header("Authorization", "Bearer access-one")
                        .cookie(new jakarta.servlet.http.Cookie("festivalRefreshToken", "refresh-one"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNoContent()).andExpect(header().string("Set-Cookie", containsString("Max-Age=0")));
        org.assertj.core.api.Assertions.assertThat(REQUESTS).extracting(RecordedRequest::path)
                .containsExactly("/api/users/me/password", "/api/users/me/password");
        org.assertj.core.api.Assertions.assertThat(REQUESTS.get(1).authorization()).isEqualTo("Bearer access-one");
        org.assertj.core.api.Assertions.assertThat(REQUESTS.get(1).body())
                .contains("\"currentPassword\":\"wrong-password\"", "\"newPassword\":\"new-password123\"");
    }

    @Test
    void passwordChangeRequiresLoginAndValidNewPasswordBeforeSsoCall() throws Exception {
        mvc.perform(patch("/api/users/me/password").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"current-password\",\"newPassword\":\"new-password123\"}"))
                .andExpect(status().isUnauthorized());

        mvc.perform(patch("/api/users/me/password").header("Authorization", "Bearer access-one")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"current-password\",\"newPassword\":\"short\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        org.assertj.core.api.Assertions.assertThat(REQUESTS).isEmpty();
    }

    @Test
    void timeoutIsMappedToServiceUnavailable() throws Exception {
        RESPONSES.add(new StubResponse(200, "{\"accessToken\":\"late\"}", null, 300));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"festival01\",\"password\":\"password123\"}"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("SSO_UNAVAILABLE"));
    }

    @Test
    void credentialsAndTokensAreNeverWrittenToSsoLogs() throws Exception {
        ch.qos.logback.classic.Logger logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(SsoAuthClient.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            enqueue(200, "{\"accessToken\":\"secret-access-token\"}", "refreshToken=secret-refresh-token; HttpOnly");
            enqueue(200, meJson("USER"));
            mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"loginId\":\"festival01\",\"password\":\"secret-password-123\"}"))
                    .andExpect(status().isOk());
            String logs = appender.list.stream().map(ILoggingEvent::getFormattedMessage)
                    .collect(java.util.stream.Collectors.joining("\n"));
            org.assertj.core.api.Assertions.assertThat(logs)
                    .doesNotContain("secret-password-123", "secret-access-token", "secret-refresh-token", "test-secret");
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void swaggerUiAndOpenApiBearerSchemeAreAvailable() throws Exception {
        mvc.perform(get("/swagger-ui.html"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", containsString("/swagger-ui/index.html")));

        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("SYU Festa 2026 API"))
                .andExpect(jsonPath("$.servers[0].url").value("http://127.0.0.1:8888"))
                .andExpect(jsonPath("$.servers[0].description").value("로컬"))
                .andExpect(jsonPath("$.servers[1].url").value("https://festa.syu-likelion.org"))
                .andExpect(jsonPath("$.servers[1].description").value("배포"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.paths['/api/auth/signup'].post.summary").value("회원가입"))
                .andExpect(jsonPath("$.paths['/api/auth/email/find-id/verify'].post.summary").value("아이디 찾기 인증번호 확인"))
                .andExpect(jsonPath("$.paths['/api/auth/email/reset-password/verify'].post.summary").value("로그인 전 비밀번호 재설정"))
                .andExpect(jsonPath("$.paths['/api/users/me'].get.summary").value("내 정보 조회"))
                .andExpect(jsonPath("$.paths['/api/users/me'].get.security[0].bearerAuth").isArray())
                .andExpect(jsonPath("$.paths['/api/users/me'].get.parameters").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/auth/logout'].post.parameters").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/qr/tokens'].post.summary").value("내 QR 토큰 발급"))
                .andExpect(jsonPath("$.paths['/api/qr/scan'].post.security[0].bearerAuth").isArray())
                .andExpect(jsonPath("$.paths['/api/qr/search'].post.security[0].bearerAuth").isArray())
                .andExpect(jsonPath("$.paths['/api/qr/users/{userUuid}/role'].patch.security[0].bearerAuth").isArray());
    }

    @Test
    void internalProfileUsesCachedClientCredentialsServiceToken() {
        enqueue(200, "{\"access_token\":\"service-access\",\"expires_in\":300}");
        enqueue(200, "{\"profiles\":[{\"userUuid\":\"" + UUID + "\",\"name\":\"홍길동\","
                + "\"studentNo\":\"20260001\",\"department\":\"컴퓨터공학과\",\"grade\":2}]}" );

        var profile = internalProfiles.getProfile(java.util.UUID.fromString(UUID));
        enqueue(200, "{\"profiles\":[{\"userUuid\":\"" + UUID + "\",\"name\":\"홍길동\"}]}" );
        internalProfiles.getProfile(java.util.UUID.fromString(UUID));

        org.assertj.core.api.Assertions.assertThat(profile.name()).isEqualTo("홍길동");
        org.assertj.core.api.Assertions.assertThat(REQUESTS).extracting(RecordedRequest::path)
                .containsExactly("/oauth2/token", "/api/internal/users/profiles/batch",
                        "/api/internal/users/profiles/batch");
        org.assertj.core.api.Assertions.assertThat(REQUESTS.get(0).query())
                .contains("grant_type=client_credentials", "user.profile.read");
        org.assertj.core.api.Assertions.assertThat(REQUESTS.get(1).authorization()).isEqualTo("Bearer service-access");
        org.assertj.core.api.Assertions.assertThat(REQUESTS.get(1).body()).contains(UUID);
    }

    private static String meJson(String ssoRole) {
        return "{\"userUuid\":\"" + UUID + "\",\"loginId\":\"festival01\",\"email\":\"student@example.com\","
                + "\"ssoRole\":\"" + ssoRole + "\",\"status\":\"ACTIVE\",\"name\":null,\"phone\":null,"
                + "\"studentNo\":null,\"department\":null,\"grade\":null,\"enrollment\":null,\"birthDate\":null,"
                + "\"createdAt\":\"2026-07-28T10:00:00\",\"updatedAt\":\"2026-07-28T10:00:00\"}";
    }

    private static void enqueue(int status, String body) { RESPONSES.add(new StubResponse(status, body, null, 0)); }
    private static void enqueue(int status, String body, String cookie) { RESPONSES.add(new StubResponse(status, body, cookie, 0)); }

    private static HttpServer startServer() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", SsoAuthIntegrationTests::handle);
            server.setExecutor(Executors.newCachedThreadPool());
            server.start();
            return server;
        } catch (IOException exception) { throw new ExceptionInInitializerError(exception); }
    }

    private static void handle(HttpExchange exchange) throws IOException {
        StubResponse response = RESPONSES.poll();
        if (response == null) response = new StubResponse(500, "{}", null, 0);
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        REQUESTS.add(new RecordedRequest(exchange.getRequestURI().getPath(), exchange.getRequestURI().getRawQuery(), body,
                exchange.getRequestHeaders().getFirst("Authorization"), exchange.getRequestHeaders().getFirst("Cookie")));
        if (response.delayMs() > 0) try { Thread.sleep(response.delayMs()); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
        if (response.setCookie() != null) exchange.getResponseHeaders().add("Set-Cookie", response.setCookie());
        byte[] bytes = response.body().getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(response.status(), bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    record StubResponse(int status, String body, String setCookie, long delayMs) { }
    record RecordedRequest(String path, String query, String body, String authorization, String cookie) { }
}
