package org.syu_likelion.Festa_2026.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
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

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    org.syu_likelion.Festa_2026.admin.AdminAccessService swaggerAdminAccess;

    @Test
    @SuppressWarnings("unchecked")
    void documentsEveryApplicationApiWithSummaryDescriptionAndResponses() throws Exception {
        org.mockito.Mockito.when(swaggerAdminAccess.authenticateForSwagger("docs-admin", null)).thenReturn(
                new org.syu_likelion.Festa_2026.auth.AuthorizedSsoExecutor.AuthorizedResult<>(
                    new org.syu_likelion.Festa_2026.admin.AdminAccessService.AdminIdentity(
                        java.util.UUID.randomUUID(), "docs", org.syu_likelion.Festa_2026.user.FestivalRole.ADMIN), null, null));
        byte[] json = mvc.perform(get("/admin/v3/api-docs").cookie(new jakarta.servlet.http.Cookie("festivalAdminAccess", "docs-admin")))
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
                        "POST /api/birthday-messages", "POST /api/bamboo/nickname", "POST /api/bamboo/messages",
                        "POST /api/timetable"),
                "202", List.of("POST /api/analytics/events"),
                "204", List.of("DELETE /api/sponsors/{id}", "DELETE /api/admin/polls/{id}",
                        "DELETE /api/performances/{id}", "DELETE /api/notices/{id}", "DELETE /api/lost-items/{id}",
                        "DELETE /api/booths/{id}", "POST /api/booths/{id}/favorite", "DELETE /api/booths/{id}/favorite",
                        "DELETE /api/birthday-messages/{id}", "DELETE /api/admin/birthday-messages/{id}",
                        "POST /api/bamboo/messages/{id}/report", "POST /api/presence/heartbeat",
                        "POST /api/auth/logout", "DELETE /api/users/me", "DELETE /api/auth/school/profile",
                        "DELETE /api/timetable/{id}"),
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
        for (String endpoint : List.of("/api/performances", "/api/performances/{id}",
                "/api/timetable", "/api/timetable/{id}")) {
            assertThat(paths.get(endpoint).get("get").get("security"))
                    .as("%s is public", endpoint).isNull();
            for (var operation : paths.get(endpoint).entrySet()) {
                if (!operation.getKey().equals("get")) {
                    assertThat(operation.getValue().get("security"))
                            .isEqualTo(List.of(Map.of("bearerAuth", List.of())));
                }
            }
        }
        assertThat(paths.get("/api/timetable/admin").get("get").get("security"))
                .isEqualTo(List.of(Map.of("bearerAuth", List.of())));
        // Compare the maintained Markdown inventory with generated runtime mappings, not a fixed count.
        Set<String> actualEndpoints = new TreeSet<>();
        paths.forEach((path, operations) -> operations.keySet().forEach(verb ->
                actualEndpoints.add(verb.toUpperCase(java.util.Locale.ROOT) + " " + path)));
        Set<String> indexedEndpoints = new TreeSet<>();
        var row = Pattern.compile("(?m)^\\|\\s*`?(GET|POST|PUT|PATCH|DELETE)`?\\s*\\|\\s*`([^`]+)`");
        var matcher = row.matcher(Files.readString(Path.of("docs/api-endpoint-index.md")));
        while (matcher.find()) indexedEndpoints.add(matcher.group(1) + " " + matcher.group(2));
        assertThat(indexedEndpoints).as("Markdown API index matches generated OpenAPI")
                .containsExactlyElementsOf(actualEndpoints);

        Map<String, Object> components = (Map<String, Object>) document.get("components");
        Map<String, Map<String, Object>> schemas = (Map<String, Map<String, Object>>) components.get("schemas");
        for (var field : Map.of("SignupRequest", "password", "ResetPasswordRequest", "newPassword").entrySet()) {
            var schema = schemas.get(field.getKey());
            assertThat((List<String>) schema.get("required")).contains(field.getValue());
            var properties = (Map<String, Map<String, Object>>) schema.get("properties");
            assertThat(properties.get(field.getValue())).containsEntry("minLength", 8)
                    .containsEntry("maxLength", 20).containsEntry("format", "password")
                    .containsEntry("writeOnly", true);
        }
        var signupProperties = (Map<String, Map<String, Object>>) schemas.get("SignupRequest").get("properties");
        assertThat(signupProperties.get("loginId")).containsEntry("minLength", 4).containsEntry("maxLength", 50);
        var chatProperties = (Map<String, Map<String, Object>>) schemas.get("BambooMessageResponse").get("properties");
        assertThat((List<String>) chatProperties.get("content").get("type")).contains("null", "string");
        assertThat((String) chatProperties.get("content").get("description")).contains("HIDDEN", "BLOCKED");
        var profileProperties = (Map<String, Object>) schemas.get("ProfileUpdateRequest").get("properties");
        assertThat(profileProperties).containsOnlyKeys("phone", "department", "grade", "enrollment");
        var profile = new org.syu_likelion.Festa_2026.user.UserDtos.ProfileUpdateRequest("01012345678", null, null, null);
        assertThat(mapper.readValue(mapper.writeValueAsBytes(profile), Map.class)).doesNotContainKey("empty");
        assertThat(profile.isEmpty()).isFalse();
        assertThat(new org.syu_likelion.Festa_2026.user.UserDtos.ProfileUpdateRequest(null, null, null, null).isEmpty()).isTrue();
        var meProperties = (Map<String, Map<String, Object>>) schemas.get("MeResponse").get("properties");
        for (String field : List.of("name", "phone", "studentNo", "department", "grade", "enrollment", "birthDate", "schoolVerifiedAt")) {
            assertThat((List<String>) meProperties.get(field).get("type")).as("MeResponse.%s accepts null", field).contains("null");
        }
        assertThat(meProperties).containsKey("schoolVerified");
        var mineProperties = (Map<String, Map<String, Object>>) schemas.get("MyBirthdayMessageResponse").get("properties");
        assertThat((List<String>) mineProperties.get("message").get("type")).contains("object", "null");
        assertThat(mineProperties.get("message")).doesNotContainKey("$ref").containsKey("properties");
        assertThat(mapper.readValue(mapper.writeValueAsBytes(
                new org.syu_likelion.Festa_2026.birthday.BirthdayMessageDtos.MyBirthdayMessageResponse(false, null)), Map.class))
                .containsEntry("written", false).containsEntry("message", null);
        for (var entry : Map.of("BambooStatusChangeResponse", "changed", "BambooCursorResponse", "cursor").entrySet()) {
            assertThat((Map<String, Object>) schemas.get(entry.getKey()).get("properties")).containsOnlyKeys(entry.getValue());
        }
        var changedResponse = (Map<String, Map<String, Object>>) paths.get("/api/admin/bamboo/messages").get("patch").get("responses");
        assertThat(mapper.writeValueAsString(changedResponse.get("200"))).contains("#/components/schemas/BambooStatusChangeResponse");
        var cursorResponse = (Map<String, Map<String, Object>>) paths.get("/admin/bamboo/cursor").get("get").get("responses");
        assertThat(mapper.writeValueAsString(cursorResponse.get("200"))).contains("#/components/schemas/BambooCursorResponse");
        assertThat(mapper.readValue(mapper.writeValueAsBytes(
                new org.syu_likelion.Festa_2026.bamboo.BambooDtos.BambooStatusChangeResponse(2)), Map.class)).containsExactlyEntriesOf(Map.of("changed", 2));
        assertThat(mapper.readValue(mapper.writeValueAsBytes(
                new org.syu_likelion.Festa_2026.bamboo.BambooDtos.BambooCursorResponse(3)), Map.class)).containsExactlyEntriesOf(Map.of("cursor", 3));
        var eventProperties = (Map<String, Map<String, Object>>) schemas.get("FrontendEventRequest").get("properties");
        assertThat((String) eventProperties.get("targetId").get("description")).contains("필수", "생략/null");
        assertThat((String) eventProperties.get("durationMs").get("description")).contains("PAGE_LEAVE", "43200000");
        assertThat((String) eventProperties.get("occurredAt").get("description")).contains("24시간", "5분");
        var boothProperties = (Map<String, Map<String, Object>>) schemas.get("BoothMutationRequest").get("properties");
        assertThat(boothProperties.get("opensAt")).containsEntry("example", "10:00:00");
        assertThat(boothProperties.get("closesAt")).containsEntry("example", "18:00:00");
        var qrProperties = (Map<String, Object>) schemas.get("QrUserView").get("properties");
        assertThat(qrProperties).doesNotContainKeys("wristband", "managementRole");
        assertThat(paths.get("/api/auth/token/refresh").get("post").get("security"))
                .isEqualTo(List.of(Map.of("refreshCookie", List.of())));
        for (String endpoint : List.of("/api/bamboo/messages", "/api/birthday-messages", "/api/polls")) {
            var responses = (Map<String, Map<String, Object>>) paths.get(endpoint).get("get").get("responses");
            assertThat((String) responses.get("403").get("description")).contains("SCHOOL_VERIFICATION_REQUIRED");
            var content = (Map<String, Map<String, Object>>) responses.get("403").get("content");
            assertThat((Map<String, Object>) content.get("application/json").get("schema"))
                    .containsEntry("$ref", "#/components/schemas/ApiError");
        }
        assertThat(schemas).containsKey("ApiError");
        Path report = Path.of("build/reports/openapi.json");
        Files.createDirectories(report.getParent());
        Files.write(report, json);
        System.out.println("Documented application API operations: " + checked);
    }
}
