package org.syu_likelion.Festa_2026.birthday;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.syu_likelion.Festa_2026.sso.SsoInternalProfileClient;
import org.syu_likelion.Festa_2026.sso.SsoProfiles.InternalUserProfile;
import org.syu_likelion.Festa_2026.user.FestivalRole;
import org.syu_likelion.Festa_2026.user.FestivalUserService;
import org.syu_likelion.Festa_2026.user.UserService;

class BirthdayMessageAdminServiceTests {
    private static final UUID AUTHOR = UUID.fromString("123e4567-e89b-12d3-a456-426614174010");
    private BirthdayMessageService messages;
    private SsoInternalProfileClient profiles;
    private FestivalUserService festivalUsers;
    private BirthdayMessageAdminService service;

    @BeforeEach
    void setUp() {
        messages = mock(BirthdayMessageService.class);
        profiles = mock(SsoInternalProfileClient.class);
        festivalUsers = mock(FestivalUserService.class);
        service = new BirthdayMessageAdminService(messages, mock(UserService.class), profiles, festivalUsers);
        when(messages.listEntities(BirthdayMessageSort.LATEST, 0, 30))
                .thenReturn(new PageImpl<>(List.of(new BirthdayMessage(AUTHOR, "축하해!",
                        "컴퓨터공학부", "2024******", "홍*동"))));
        when(profiles.getProfiles(List.of(AUTHOR))).thenReturn(List.of(profile()));
    }

    @Test
    void staffOnlySeesAcademicIdentity() {
        var author = service.listAs(Set.of(FestivalRole.STAFF), BirthdayMessageSort.LATEST, 0, 30)
                .items().getFirst().author();

        assertThat(author.name()).isEqualTo("홍길동");
        assertThat(author.studentNo()).isEqualTo("2024100920");
        assertThat(author.department()).isEqualTo("컴퓨터공학부");
        assertThat(author.grade()).isEqualTo(3);
        assertThat(author.email()).isNull();
        assertThat(author.phone()).isNull();
        assertThat(author.userUuid()).isNull();
    }

    @Test
    void adminAlsoSeesContactInformation() {
        var author = service.listAs(Set.of(FestivalRole.ADMIN), BirthdayMessageSort.LATEST, 0, 30)
                .items().getFirst().author();

        assertThat(author.email()).isEqualTo("user@example.com");
        assertThat(author.phone()).isEqualTo("01012345678");
        assertThat(author.loginId()).isNull();
    }

    @Test
    void superAdminSeesFullProfileAndFestivalRoles() {
        when(festivalUsers.getRoles(AUTHOR)).thenReturn(Set.of(FestivalRole.USER));

        var author = service.listAs(Set.of(FestivalRole.SUPER_ADMIN), BirthdayMessageSort.LATEST, 0, 30)
                .items().getFirst().author();

        assertThat(author.userUuid()).isEqualTo(AUTHOR);
        assertThat(author.loginId()).isEqualTo("user01");
        assertThat(author.ssoRole()).isEqualTo("USER");
        assertThat(author.enrollment()).isEqualTo("ENROLLED");
        assertThat(author.festivalRoles()).containsExactly(FestivalRole.USER);
    }

    private InternalUserProfile profile() {
        return new InternalUserProfile(AUTHOR, "user01", "user@example.com", "USER", "ACTIVE",
                "홍길동", "01012345678", "2024100920", "컴퓨터공학부", 3,
                "ENROLLED", "2005-01-01", "2026-01-01T00:00:00", "2026-08-01T00:00:00");
    }
}
