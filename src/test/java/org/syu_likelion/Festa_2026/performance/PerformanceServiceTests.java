package org.syu_likelion.Festa_2026.performance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.syu_likelion.Festa_2026.auth.AuthorizedSsoExecutor.AuthorizedResult;
import org.syu_likelion.Festa_2026.error.ApiException;
import org.syu_likelion.Festa_2026.performance.PerformanceDtos.PerformanceMutationRequest;
import org.syu_likelion.Festa_2026.user.FestivalRole;
import org.syu_likelion.Festa_2026.user.UserDtos.MeResponse;
import org.syu_likelion.Festa_2026.user.UserService;

class PerformanceServiceTests {
    private static final Instant NOW = Instant.parse("2026-08-10T12:00:00Z");
    private static final UUID USER_UUID = UUID.fromString("123e4567-e89b-12d3-a456-426614174099");

    private FestivalPerformanceRepository repository;
    private UserService users;
    private MediaStorage storage;
    private PerformanceService service;

    @BeforeEach
    void setUp() {
        repository = mock(FestivalPerformanceRepository.class);
        users = mock(UserService.class);
        storage = mock(MediaStorage.class);
        service = new PerformanceService(repository, users, storage,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void listIncludesUnpublishedPerformanceAsTba() {
        authenticateAs(FestivalRole.USER);
        FestivalPerformance visible = entity(NOW.minusSeconds(1));
        FestivalPerformance hidden = entity(NOW.plusSeconds(1));
        hidden.addMedia(PerformanceMedia.linked(PerformanceMediaKind.IMAGE, "https://example.com/secret.jpg"));
        hidden.addMedia(PerformanceMedia.linked(PerformanceMediaKind.VIDEO, "https://example.com/secret.mp4"));
        when(repository.findAllByOrderByStartsAtAsc()).thenReturn(List.of(visible, hidden));

        var result = service.listVisible("access", "refresh");

        assertThat(result.body()).hasSize(2);
        assertThat(result.body().getFirst().teamName()).isEqualTo("천보 밴드");
        assertThat(result.body().getFirst().published()).isTrue();
        var tba = result.body().get(1);
        assertThat(tba.teamName()).isEqualTo("TBA");
        assertThat(tba.description()).isEmpty();
        assertThat(tba.memberNames()).isEmpty();
        assertThat(tba.links()).isEmpty();
        assertThat(tba.images()).isEmpty();
        assertThat(tba.videos()).isEmpty();
        assertThat(tba.published()).isFalse();
        assertThat(tba.startsAt()).isEqualTo(hidden.getStartsAt());
        assertThat(tba.endsAt()).isEqualTo(hidden.getEndsAt());
        assertThat(tba.category()).isEqualTo(hidden.getCategory());
        verify(repository).findAllByOrderByStartsAtAsc();
    }

    @Test
    void publicDetailIsMaskedForEveryRoleWhileAdminViewKeepsOriginal() {
        var hidden = entity(NOW.plusNanos(1));
        when(repository.findById(1L)).thenReturn(java.util.Optional.of(hidden));
        for (FestivalRole role : FestivalRole.values()) {
            authenticateAs(role);
            var body = service.getVisible(1L, "access", null).body();
            assertThat(body.teamName()).isEqualTo("TBA");
            assertThat(body.description()).isEmpty();
            assertThat(body.memberNames()).isEmpty();
            assertThat(body.links()).isEmpty();
            assertThat(body.published()).isFalse();
        }
        assertThat(service.getAdmin(1L).teamName()).isEqualTo(hidden.getTeamName());
    }

    @Test
    void detailRevealsOriginalExactlyAtPublicationTimeAndMissingIdRemains404() {
        authenticateAs(FestivalRole.USER);
        var visible = entity(NOW);
        visible.addMedia(PerformanceMedia.linked(PerformanceMediaKind.IMAGE, "https://example.com/team.jpg"));
        when(repository.findById(1L)).thenReturn(java.util.Optional.of(visible));
        var body = service.getVisible(1L, "access", null).body();
        assertThat(body.published()).isTrue();
        assertThat(body.teamName()).isEqualTo(visible.getTeamName());
        assertThat(body.description()).isEqualTo(visible.getDescription());
        assertThat(body.memberNames()).isEqualTo(visible.getMemberNames());
        assertThat(body.images()).hasSize(1);
        assertThatThrownBy(() -> service.getVisible(99L, "access", null))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.status().value()).isEqualTo(404);
                    assertThat(exception.code()).isEqualTo("PERFORMANCE_NOT_FOUND");
                });
    }

    @Test
    void ordinaryUserCannotCreatePerformance() {
        authenticateAs(FestivalRole.USER);

        assertThatThrownBy(() -> service.create("access", null, request(List.of(), List.of())))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.status().value()).isEqualTo(403);
                    assertThat(exception.code()).isEqualTo("PERFORMANCE_MANAGE_FORBIDDEN");
                });
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void adminCanCreatePerformanceWithLinks() {
        authenticateAs(FestivalRole.ADMIN);
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.create("access", null,
                request(List.of("https://example.com/profile"), List.of("https://example.com/photo.webp")));

        assertThat(result.body().category()).isEqualTo(PerformanceCategory.CLUB);
        assertThat(result.body().memberNames()).containsExactly("김학생", "이학생");
        assertThat(result.body().links()).containsExactly("https://example.com/profile");
        assertThat(result.body().images()).hasSize(1);
    }

    @Test
    void imageLinksAndUploadsTogetherCannotExceedThree() {
        var file = new org.springframework.mock.web.MockMultipartFile(
                "imageFiles", "stage.webp", "image/webp", new byte[]{1});

        assertThatThrownBy(() -> service.createAs(USER_UUID,
                request(List.of(), List.of(
                        "https://example.com/1.webp",
                        "https://example.com/2.webp",
                        "https://example.com/3.webp")), List.of(file), List.of()))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.code()).isEqualTo("TOO_MANY_MEDIA"));
        verify(storage, never()).store(any(), any());
    }

    @Test
    void endTimeMustBeAfterStartTime() {
        PerformanceMutationRequest invalid = new PerformanceMutationRequest(
                PerformanceCategory.INDIVIDUAL, "개인 공연", List.of("홍길동"),
                NOW.plusSeconds(3600), NOW.plusSeconds(3600), "설명",
                List.of(), List.of(), List.of(), NOW);

        assertThatThrownBy(() -> service.createAs(USER_UUID, invalid, List.of(), List.of()))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.code()).isEqualTo("INVALID_PERFORMANCE_TIME"));
    }

    private void authenticateAs(FestivalRole role) {
        MeResponse me = new MeResponse(USER_UUID, "user01", "user@example.com", "USER", "ACTIVE",
                null, null, null, null, null, null, null, null, null, Set.of(role));
        when(users.getMe("access", "refresh")).thenReturn(new AuthorizedResult<>(me, null, null));
        when(users.getMe("access", null)).thenReturn(new AuthorizedResult<>(me, null, null));
    }

    private PerformanceMutationRequest request(List<String> links, List<String> imageUrls) {
        return new PerformanceMutationRequest(PerformanceCategory.CLUB, "천보 밴드",
                List.of("김학생", "이학생"), NOW.plusSeconds(3600), NOW.plusSeconds(7200),
                "축제 무대 공연팀입니다.", links, imageUrls, List.of(), NOW.minusSeconds(60));
    }

    private FestivalPerformance entity(Instant publishedAt) {
        return new FestivalPerformance(PerformanceCategory.CLUB, "천보 밴드",
                List.of("김학생", "이학생"), NOW.plusSeconds(3600), NOW.plusSeconds(7200),
                "축제 무대 공연팀입니다.", List.of(), publishedAt, USER_UUID);
    }
}
