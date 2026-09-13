package org.syu_likelion.Festa_2026.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class OpenApiDocumentationIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping mappings;

    @Test
    @SuppressWarnings("unchecked")
    void documentsEveryApplicationApiWithSummaryDescriptionAndResponses() throws Exception {
        byte[] json = mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        Map<String, Object> document = mapper.readValue(json, Map.class);
        Map<String, Map<String, Map<String, Object>>> paths =
                (Map<String, Map<String, Map<String, Object>>>) document.get("paths");
        assertThat(paths).isNotEmpty();
        int checked = 0;
        for (var entry : mappings.getHandlerMethods().entrySet()) {
            var handler = entry.getValue();
            if (!handler.getBeanType().getPackageName().startsWith("org.syu_likelion.Festa_2026")) continue;
            boolean jsonHandler = AnnotatedElementUtils.hasAnnotation(handler.getBeanType(), RestController.class)
                    || handler.hasMethodAnnotation(ResponseBody.class);
            for (String path : entry.getKey().getPatternValues()) {
                // Include browser SSO redirects, but exclude HTML pages and form submissions.
                if (!jsonHandler && !path.startsWith("/api/") && !path.equals("/auth/sso/callback")) continue;
                assertThat(paths).as("Swagger path for %s", handler).containsKey(path);
                for (var method : entry.getKey().getMethodsCondition().getMethods()) {
                    String verb = method.name().toLowerCase(java.util.Locale.ROOT);
                    var operation = paths.get(path).get(verb);
                    assertThat(operation).as("%s %s", verb, path).isNotNull();
                    assertThat((String) operation.get("summary")).as("%s %s summary", verb, path).isNotBlank();
                    assertThat((String) operation.get("description")).as("%s %s description", verb, path).isNotBlank();
                    assertThat(operation.get("tags")).as("%s %s tags", verb, path).isNotNull();
                    assertThat((Map<?, ?>) operation.get("responses")).as("%s %s responses", verb, path).isNotEmpty();
                    checked++;
                }
            }
        }
        assertThat(checked).isGreaterThan(100);
        Map<String, List<String>> expectedResponses = Map.of(
                "201", List.of("POST /api/sponsors", "POST /api/admin/polls", "POST /api/polls/{id}/submissions",
                        "POST /api/performances", "POST /api/notices", "POST /api/lost-items", "POST /api/booths",
                        "POST /api/birthday-messages", "POST /api/bamboo/nickname", "POST /api/bamboo/messages"),
                "202", List.of("POST /api/analytics/events"),
                "204", List.of("DELETE /api/sponsors/{id}", "DELETE /api/admin/polls/{id}",
                        "DELETE /api/performances/{id}", "DELETE /api/notices/{id}", "DELETE /api/lost-items/{id}",
                        "DELETE /api/booths/{id}", "POST /api/booths/{id}/favorite", "DELETE /api/booths/{id}/favorite",
                        "DELETE /api/birthday-messages/{id}", "DELETE /api/admin/birthday-messages/{id}",
                        "POST /api/bamboo/messages/{id}/report", "POST /api/presence/heartbeat",
                        "POST /api/auth/logout", "DELETE /api/users/me", "DELETE /api/auth/school/profile"),
                "302", List.of("GET /api/auth/school/authorize", "GET /auth/sso/callback"));
        expectedResponses.forEach((code, endpoints) -> endpoints.forEach(endpoint -> {
            String[] parts = endpoint.split(" ", 2);
            var operation = paths.get(parts[1]).get(parts[0].toLowerCase(java.util.Locale.ROOT));
            var responses = (Map<String, Map<String, Object>>) operation.get("responses");
            assertThat(responses).as("%s success status", endpoint).containsKey(code).doesNotContainKey("200");
            if (code.equals("204") || code.equals("302")) {
                assertThat(responses.get(code)).as("%s has no response body", endpoint).doesNotContainKey("content");
            } else {
                assertThat(responses.get(code)).as("%s retains response schema", endpoint).containsKey("content");
            }
        }));
        for (String endpoint : List.of("/api/sponsors", "/api/sponsors/{id}")) {
            for (var operation : paths.get(endpoint).entrySet()) {
                if (!operation.getKey().equals("get")) {
                    assertThat(operation.getValue().get("security"))
                            .as("%s %s Bearer authentication", operation.getKey(), endpoint)
                            .isEqualTo(List.of(Map.of("bearerAuth", List.of())));
                }
            }
        }
        Path report = Path.of("build/reports/openapi.json");
        Files.createDirectories(report.getParent());
        Files.write(report, json);
        System.out.println("Documented application API operations: " + checked);
    }
}
