package org.syu_likelion.Festa_2026.notice;

import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.syu_likelion.Festa_2026.admin.AdminAccessService;
import org.syu_likelion.Festa_2026.auth.AuthorizedSsoExecutor.AuthorizedResult;
import org.syu_likelion.Festa_2026.error.ApiException;
import org.syu_likelion.Festa_2026.user.FestivalRole;
import org.syu_likelion.Festa_2026.user.UserService;
import org.syu_likelion.Festa_2026.user.UserDtos.MeResponse;
import org.syu_likelion.Festa_2026.notice.NoticeDtos.NoticeMutationRequest;

@SpringBootTest(properties = {
        "sso.client-id=test-client", "sso.client-secret=test-secret", "admin.cookie-secure=false",
        "spring.datasource.url=jdbc:h2:mem:notices;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
@AutoConfigureMockMvc
class NoticeIntegrationTests {
    private static final UUID ACTOR = UUID.randomUUID();
    @Autowired MockMvc mvc;
    @Autowired NoticeService notices;
    @Autowired NoticeRepository repository;
    @MockitoBean NoticeAttachmentStorage storage;
    @MockitoBean UserService users;
    @MockitoBean AdminAccessService admins;

    @BeforeEach
    void setup() {
        repository.deleteAll();
        when(storage.store(any())).thenAnswer(call -> {
            var file = call.getArgument(0, org.springframework.web.multipart.MultipartFile.class);
            String key = UUID.randomUUID().toString();
            return new NoticeAttachmentStorage.StoredAttachment("https://example.com/" + key,
                    key, file.getOriginalFilename(), file.getContentType(), file.getSize());
        });
        var me = new MeResponse(ACTOR, "staff", "staff@example.com", "USER", "ACTIVE",
                "운영자", null, null, null, null, null, null, null, null, Set.of(FestivalRole.STAFF));
        when(users.getMe("staff", null)).thenReturn(new AuthorizedResult<>(me, "rotated", null));
        when(users.authenticateEarly(any(), eq("staff"), isNull()))
                .thenReturn(new AuthorizedResult<>(me, null, null));
        when(admins.authenticate("staff", null)).thenReturn(new AuthorizedResult<>(
                new AdminAccessService.AdminIdentity(ACTOR, "운영자", FestivalRole.STAFF), null, null));
    }

    @Test
    void publicCrudKeepsMixedAttachmentsAndPinnedNoticesFirst() throws Exception {
        var created = notices.createAs(ACTOR, "운영자", data("고정 공지", true), List.of(
                file("photo.png", "image/png"), file("clip.mp4", "video/mp4"), file("안내.pdf", "application/pdf")));
        var other = notices.createAs(ACTOR, "운영자", data("새 공지", false), List.of());
        mvc.perform(get("/api/notices")).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(created.id()))
                .andExpect(jsonPath("$.items[0].files[0].originalFilename").value("안내.pdf"))
                .andExpect(jsonPath("$.items[0].media.length()").value(2))
                .andExpect(jsonPath("$.items[0].attachments").doesNotExist());
        mvc.perform(get("/api/notices/{id}", created.id())).andExpect(status().isOk())
                .andExpect(jsonPath("$.viewCount").value(1));
        var oldAttachment = created.media().get(0);
        var updated = notices.updateAs(created.id(), ACTOR, data("수정 공지", true),
                List.of(oldAttachment.id()), List.of(file("new.pdf", "application/pdf")));
        assertThat(updated.files()).extracting(a -> a.originalFilename())
                .containsExactly("안내.pdf", "new.pdf");
        assertThat(updated.media()).extracting(a -> a.originalFilename()).containsExactly("clip.mp4");
        assertThat(updated.media().get(0).displayOrder()).isZero();
        assertThat(updated.files()).extracting(a -> a.displayOrder()).containsExactly(0, 1);
        verify(storage).delete(oldAttachment.url().substring("https://example.com/".length()));
        notices.changePinnedAs(other.id(), ACTOR, true);
        assertThat(notices.listPublic(NoticeSort.OLDEST, 0, 20).items().get(0).id()).isEqualTo(other.id());
        notices.deleteAs(created.id());
        mvc.perform(get("/api/notices/{id}", created.id())).andExpect(status().isNotFound());
    }

    @Test
    void uploadRollbackRemovesNewFilesAndPreservesExistingAttachments() {
        var created = notices.createAs(ACTOR, "운영자", data("원본", false), List.of(file("old.pdf", "application/pdf")));
        doReturn(new NoticeAttachmentStorage.StoredAttachment(
                "https://example.com/new", "new", "new.pdf", "application/pdf", 1))
                .doThrow(new IllegalStateException("upload failed")).when(storage).store(any());
        assertThatThrownBy(() -> notices.updateAs(created.id(), ACTOR, data("변경", false),
                List.of(created.files().get(0).id()),
                List.of(file("new.pdf", "application/pdf"), file("fail.pdf", "application/pdf"))))
                .isInstanceOf(IllegalStateException.class);
        verify(storage).delete("new");
        verify(storage, never()).delete(created.files().get(0).url().substring("https://example.com/".length()));
        assertThat(notices.getAdmin(created.id()).title()).isEqualTo("원본");
        assertThat(notices.getAdmin(created.id()).files()).hasSize(1);
    }

    @Test
    void invalidAttachmentRemovalAndLimitDoNotUpload() {
        var created = notices.createAs(ACTOR, "운영자", data("공지", false), List.of());
        assertThatThrownBy(() -> notices.updateAs(created.id(), ACTOR, data("공지", false), List.of(999L), List.of()))
                .isInstanceOf(ApiException.class);
        var files = java.util.stream.IntStream.range(0, 11)
                .mapToObj(i -> (org.springframework.web.multipart.MultipartFile) file("a.pdf", "application/pdf")).toList();
        assertThatThrownBy(() -> notices.createAs(ACTOR, "운영자", data("공지", false), files))
                .isInstanceOf(ApiException.class);
        verify(storage, never()).store(any());
    }

    @Test
    void multipartApiRequiresStaffAndValidContentAndPreservesTokenRotation() throws Exception {
        var data = new MockMultipartFile("data", "", "application/json",
                "{\"title\":\"공지\",\"content\":\"내용\",\"pinned\":true}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        mvc.perform(multipart("/api/notices").file(data)).andExpect(status().isUnauthorized());
        mvc.perform(multipart("/api/notices").file(data).file(file("a.pdf", "application/pdf"))
                        .header("Authorization", "Bearer staff"))
                .andExpect(status().isCreated()).andExpect(header().string("X-Access-Token", "rotated"))
                .andExpect(jsonPath("$.files[0].contentType").value("application/pdf"));
        var invalid = new MockMultipartFile("data", "", "application/json", "{\"title\":\"\",\"content\":\"내용\"}".getBytes());
        mvc.perform(multipart("/api/notices").file(invalid).header("Authorization", "Bearer staff"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void multipartSeparatesMediaAndDownloadsAndRejectsWrongGroups() throws Exception {
        var json = new MockMultipartFile("data", "", "application/json",
                "{\"title\":\"공지\",\"content\":\"본문\",\"pinned\":false}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        mvc.perform(multipart("/api/notices").file(json)
                        .file(file("photo.png", "image/png")).file(file("video.mp4", "video/mp4"))
                        .file(file("guide.pdf", "application/pdf")).header("Authorization", "Bearer staff"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.media.length()").value(2))
                .andExpect(jsonPath("$.media[0].contentType").value("image/png"))
                .andExpect(jsonPath("$.media[1].contentType").value("video/mp4"))
                .andExpect(jsonPath("$.files.length()").value(1))
                .andExpect(jsonPath("$.files[0].originalFilename").value("guide.pdf"))
                .andExpect(jsonPath("$.attachments").doesNotExist());
        clearInvocations(storage);
        mvc.perform(multipart("/api/notices").file(json)
                        .file(new MockMultipartFile("media", "guide.pdf", "application/pdf", new byte[]{1}))
                        .header("Authorization", "Bearer staff"))
                .andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/notices").file(json)
                        .file(new MockMultipartFile("files", "photo.png", "image/png", new byte[]{1}))
                        .header("Authorization", "Bearer staff"))
                .andExpect(status().isBadRequest());
        verify(storage, never()).store(any());
    }

    @Test
    void ordinaryUserCannotMutateNotices() throws Exception {
        var me = new MeResponse(ACTOR, "user", "user@example.com", "USER", "ACTIVE",
                "사용자", null, null, null, null, null, null, null, null, Set.of(FestivalRole.USER));
        when(users.getMe("user", null)).thenReturn(new AuthorizedResult<>(me, null, null));
        when(users.authenticateEarly(any(), eq("user"), isNull()))
                .thenReturn(new AuthorizedResult<>(me, null, null));
        var created = notices.createAs(ACTOR, "운영자", data("공지", false), List.of());
        mvc.perform(multipart("/api/notices").header("Authorization", "Bearer user"))
                .andExpect(status().isForbidden());
        mvc.perform(multipart(org.springframework.http.HttpMethod.PATCH, "/api/notices/{id}", created.id())
                        .header("Authorization", "Bearer user"))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/notices/{id}/pin", created.id()).header("Authorization", "Bearer user")
                        .contentType("application/json").content("{\"pinned\":true}"))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/notices/{id}", created.id()).header("Authorization", "Bearer user"))
                .andExpect(status().isForbidden());
        assertThat(notices.getAdmin(created.id()).pinned()).isFalse();
    }

    @Test
    void multipartPatchRemovesOnlySelectedAttachments() throws Exception {
        var created = notices.createAs(ACTOR, "운영자", data("안내", false),
                List.of(file("old.pdf", "application/pdf")));
        var json = new MockMultipartFile("data", "", "application/json",
                "{\"title\":\"수정\",\"content\":\"본문\",\"pinned\":true}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        mvc.perform(multipart(org.springframework.http.HttpMethod.PATCH, "/api/notices/{id}", created.id())
                        .file(json).file(file("new.pdf", "application/pdf"))
                        .param("removeAttachmentIds", created.files().get(0).id().toString())
                        .header("Authorization", "Bearer staff"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.pinned").value(true))
                .andExpect(jsonPath("$.files.length()").value(1))
                .andExpect(jsonPath("$.files[0].originalFilename").value("new.pdf"));
    }

    @Test
    void adminFormsRenderMixedMediaAndSaveWithCsrf() throws Exception {
        var created = notices.createAs(ACTOR, "운영자", data("안내", false), List.of(
                file("image.png", "image/png"), file("video.mp4", "video/mp4"), file("file.pdf", "application/pdf")));
        Cookie cookie = new Cookie("festivalAdminAccess", "staff");
        mvc.perform(get("/admin/notices").cookie(cookie)).andExpect(status().isOk())
                .andExpect(content().string(containsString("일반 공지 관리")));
        mvc.perform(get("/admin/notices/new").cookie(cookie)).andExpect(status().isOk());
        mvc.perform(get("/admin/notices/{id}/edit", created.id()).cookie(cookie)).andExpect(status().isOk())
                .andExpect(content().string(containsString("<video")))
                .andExpect(content().string(containsString("file.pdf")))
                .andExpect(content().string(containsString("다운로드 파일")))
                .andExpect(content().string(containsString("name=\"mediaFiles\"")))
                .andExpect(content().string(containsString("name=\"attachmentFiles\"")));
        mvc.perform(multipart("/admin/notices")
                        .file(new MockMultipartFile("mediaFiles", "a.png", "image/png", new byte[]{1}))
                        .file(new MockMultipartFile("attachmentFiles", "a.pdf", "application/pdf", new byte[]{1}))
                        .cookie(cookie).with(csrf())
                        .param("title", "새 안내").param("content", "본문").param("pinned", "true"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/admin/notices"));
        var saved = notices.listPublic(0, 20).items().get(0);
        assertThat(saved.title()).isEqualTo("새 안내");
        assertThat(saved.media()).hasSize(1);
        assertThat(saved.files()).hasSize(1);
    }

    private NoticeMutationRequest data(String title, boolean pinned) { return new NoticeMutationRequest(title, "본문", pinned); }
    private MockMultipartFile file(String name, String type) { return new MockMultipartFile(type.startsWith("image/") || type.startsWith("video/") ? "media" : "files", name, type, new byte[]{1}); }
}
