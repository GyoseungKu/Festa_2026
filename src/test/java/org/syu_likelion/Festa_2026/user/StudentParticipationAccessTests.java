package org.syu_likelion.Festa_2026.user;

import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.http.MediaType;
import org.syu_likelion.Festa_2026.auth.AuthorizedSsoExecutor.AuthorizedResult;
import org.syu_likelion.Festa_2026.bamboo.BambooIdentityCache;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:participation;MODE=MySQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class StudentParticipationAccessTests {
    @Autowired MockMvc mvc;
    @Autowired FestivalUserRepository repository;
    @Autowired BambooIdentityCache identities;
    @MockitoSpyBean UserService users;
    UUID id;
    String token;

    @BeforeEach void setup() {
        id = UUID.randomUUID();
        token = id.toString();
        repository.saveAndFlush(new FestivalUser(id));
        var me = new UserDtos.MeResponse(id, "student", null, "USER", "ACTIVE", null, null,
                null, null, null, null, null, null, null, Set.of(FestivalRole.USER));
        doReturn(new AuthorizedResult<>(me, "new-access", null)).when(users).getMe(token, null);
    }

    private List<MockHttpServletRequestBuilder> requests() {
        return List.of(get("/api/bamboo"), get("/api/bamboo/nickname/suggest"),
                post("/api/bamboo/nickname").content("{\"nickname\":\"졸린 오리\"}"),
                get("/api/bamboo/messages"), get("/api/bamboo/messages").param("before", "10"),
                post("/api/bamboo/messages").content("{\"content\":\"안녕하세요\"}"),
                post("/api/bamboo/messages/1/report").content("{\"reason\":\"SPAM\"}"),
                get("/api/birthday-messages"), get("/api/birthday-messages/1"), get("/api/birthday-messages/me"),
                post("/api/birthday-messages").content("{\"content\":\"생일 축하해\"}"),
                delete("/api/birthday-messages/1"), put("/api/birthday-messages/1/heart"),
                delete("/api/birthday-messages/1/heart"),
                get("/api/polls"), get("/api/polls/1"), get("/api/polls/1/results"),
                get("/api/polls/1/submissions/me"),
                post("/api/polls/1/submissions").content("{\"answers\":[]}"));
    }

    private void assertBlocked() throws Exception {
        for (var request : requests()) {
            mvc.perform(request.header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("SCHOOL_VERIFICATION_REQUIRED"));
        }
    }

    @Test void allUserRoutesRejectUnverifiedAndRevokedStudentsEvenWithIdentityCache() throws Exception {
        assertBlocked();
        var student = repository.findByUserUuid(id).orElseThrow();
        student.verifySchoolByAdmin(Instant.now());
        repository.saveAndFlush(student);
        identities.store(token, id);
        mvc.perform(get("/api/bamboo").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        student.revokeSchoolVerification();
        repository.saveAndFlush(student);
        assertBlocked();
    }

    @Test void verifiedStudentsCanReadAllThreeFeaturesAndTokensStillRotate() throws Exception {
        var student = repository.findByUserUuid(id).orElseThrow();
        student.verifySchoolByAdmin(Instant.now());
        repository.saveAndFlush(student);
        for (String path : List.of("/api/bamboo", "/api/birthday-messages", "/api/polls")) {
            mvc.perform(get(path).header("Authorization", "Bearer " + token)).andExpect(status().isOk())
                    .andExpect(header().string("X-Access-Token", "new-access"));
        }
        mvc.perform(post("/api/birthday-messages").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"생일 축하해\"}"))
                .andExpect(status().isCreated());
    }

    @Test void anonymousBirthdayReadsRequireLogin() throws Exception {
        mvc.perform(get("/api/birthday-messages")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/birthday-messages/1")).andExpect(status().isUnauthorized());
    }
}
