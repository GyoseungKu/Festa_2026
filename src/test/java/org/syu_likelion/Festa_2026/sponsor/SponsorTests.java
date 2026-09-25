package org.syu_likelion.Festa_2026.sponsor;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import java.util.*;
import java.math.BigDecimal;
import java.time.LocalTime;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.syu_likelion.Festa_2026.admin.*;
import org.syu_likelion.Festa_2026.auth.AuthorizedSsoExecutor.AuthorizedResult;
import org.syu_likelion.Festa_2026.booth.*;
import org.syu_likelion.Festa_2026.user.FestivalRole;
import org.syu_likelion.Festa_2026.error.ApiException;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:sponsors;MODE=MySQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class SponsorTests {
    @Autowired SponsorService service;
    @Autowired SponsorRepository repository;
    @Autowired BoothService booths;
    @Autowired MockMvc mvc;
    @Autowired PlatformTransactionManager transactions;
    @MockitoBean SponsorImageStorage storage;
    @MockitoBean AdminAccessService access;
    final UUID actor = UUID.randomUUID();
    MockMultipartFile image() { return new MockMultipartFile("image", "logo.png", "image/png", new byte[]{1,2,3}); }

    @BeforeEach void setup() {
        repository.deleteAll();
        when(storage.store(any())).thenReturn(new SponsorImageStorage.StoredImage("https://cdn.test/logo.png", "logo"));
        when(access.authenticate("admin", null)).thenReturn(new AuthorizedResult<>(
                new AdminAccessService.AdminIdentity(actor, "관리자", FestivalRole.ADMIN), null, null));
    }

    @Test void publicListAndDetailDoNotRequireLoginOrExposeStorageKey() throws Exception {
        var item = service.save(null, actor, "협찬사", "소개", null, image());
        mvc.perform(get("/api/sponsors")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("협찬사"))
                .andExpect(jsonPath("$[0].storageKey").doesNotExist());
        mvc.perform(get("/api/sponsors/" + item.id())).andExpect(status().isOk())
                .andExpect(jsonPath("$.imageUrl").value("https://cdn.test/logo.png"));
    }

    @Test void createRequiresImageAndRejectsUnknownBoothBeforeUploading() {
        assertThatThrownBy(() -> service.save(null, actor, "협찬사", "소개", null, null)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.save(null, actor, "협찬사", "소개", Long.MAX_VALUE, image())).isInstanceOf(ApiException.class);
        verify(storage, never()).store(any());
    }

    @Test void replacementDeletesOldFileOnlyAfterCommitAndRollbackCleansNewFile() {
        var item = service.save(null, actor, "협찬사", "소개", null, image());
        when(storage.store(any())).thenReturn(new SponsorImageStorage.StoredImage("https://cdn.test/new.png", "new"));
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            service.save(item.id(), actor, "수정", "소개", null, image());
            verify(storage, never()).delete("logo");
            tx.setRollbackOnly();
        });
        verify(storage).delete("new");
        assertThat(service.get(item.id()).imageUrl()).endsWith("logo.png");
        service.save(item.id(), actor, "수정", "소개", null, image());
        verify(storage).delete("logo");
        service.delete(item.id());
        assertThat(repository.count()).isZero();
    }

    @Test void boothDeletionRetainsSponsorAndClearsLink() {
        var booth = booths.createAs(actor, new BoothDtos.BoothMutationRequest(
                BigDecimal.ZERO, BigDecimal.ZERO, "협찬 부스", "운영", "소개",
                LocalTime.of(9,0), LocalTime.of(18,0), false, List.of(), BoothCategory.GENERAL), List.of(), List.of());
        var item = service.save(null, actor, "협찬사", "소개", booth.booth().id(), image());
        assertThat(item.boothName()).isEqualTo("협찬 부스");
        booths.deleteAs(booth.booth().id());
        assertThat(service.get(item.id()).boothId()).isNull();
    }

    @Test void adminCanRenderCreateEditAndSaveForms() throws Exception {
        Cookie cookie = new Cookie("festivalAdminAccess", "admin");
        mvc.perform(get("/admin/sponsors/new").cookie(cookie)).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("협찬사 등록")));
        mvc.perform(multipart("/admin/sponsors").file(image()).param("name","협찬사")
                .param("description","소개").cookie(cookie).with(csrf()))
                .andExpect(status().is3xxRedirection());
        var item = service.list().getFirst();
        mvc.perform(get("/admin/sponsors/" + item.id() + "/edit").cookie(cookie)).andExpect(status().isOk());
        mvc.perform(get("/admin/sponsors").cookie(cookie)).andExpect(status().isOk());
    }

    @Test void staffCannotManageAndAnonymousUploadsAreRejected() throws Exception {
        when(access.authenticate("staff", null)).thenReturn(new AuthorizedResult<>(
                new AdminAccessService.AdminIdentity(actor, "스태프", FestivalRole.STAFF), null, null));
        mvc.perform(get("/admin/sponsors").cookie(new Cookie("festivalAdminAccess","staff")))
                .andExpect(status().isForbidden());
        mvc.perform(multipart("/api/sponsors").file(image()).param("name","협찬사").param("description","소개"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/admin/sponsors").cookie(new Cookie("festivalAdminAccess","admin")))
                .andExpect(status().isForbidden());
        verify(storage, never()).store(any());
    }
}

