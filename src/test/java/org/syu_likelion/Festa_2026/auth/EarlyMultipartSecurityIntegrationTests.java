package org.syu_likelion.Festa_2026.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
class EarlyMultipartSecurityIntegrationTests {
    @Autowired MockMvc mvc;

    @Test
    void everyProtectedMultipartEndpointRejectsBeforeBodyBinding() throws Exception {
        List<Request> requests = List.of(
                new Request("POST", "/api/booths/1/media/images"),
                new Request("POST", "/api/booths/1/media/videos"),
                new Request("POST", "/api/performances/1/media/images"),
                new Request("POST", "/api/performances/1/media/videos"),
                new Request("POST", "/api/lost-items"),
                new Request("PATCH", "/api/lost-items/1"));

        for (Request request : requests) {
            MockHttpServletRequestBuilder builder = "PATCH".equals(request.method())
                    ? patch(request.path()) : post(request.path());
            mvc.perform(builder.contentType(MediaType.MULTIPART_FORM_DATA)
                            .content("deliberately malformed multipart body"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        }
    }

    private record Request(String method, String path) { }
}
