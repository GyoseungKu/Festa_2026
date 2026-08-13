package org.syu_likelion.Festa_2026.lostitem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.syu_likelion.Festa_2026.auth.AuthorizedSsoExecutor.AuthorizedResult;
import org.syu_likelion.Festa_2026.error.ApiException;
import org.syu_likelion.Festa_2026.lostitem.LostItemDtos.LostItemMutationRequest;
import org.syu_likelion.Festa_2026.lostitem.LostItemDtos.LostItemResponse;
import org.syu_likelion.Festa_2026.user.FestivalRole;
import org.syu_likelion.Festa_2026.user.UserDtos.MeResponse;
import org.syu_likelion.Festa_2026.user.UserService;

class LostItemApiServiceTests {
    private static final UUID USER_UUID = UUID.fromString("123e4567-e89b-12d3-a456-426614174010");

    private LostItemService lostItems;
    private UserService users;
    private LostItemApiService api;

    @BeforeEach
    void setUp() {
        lostItems = mock(LostItemService.class);
        users = mock(UserService.class);
        api = new LostItemApiService(lostItems, users);
    }

    @Test
    void staffCanCreateAndRotatedTokensArePreserved() {
        authenticateAs(FestivalRole.STAFF, "축제 스태프", "staff01", "new-access", "new-refresh");
        LostItemResponse response = response();
        when(lostItems.createAs(USER_UUID, "축제 스태프", request(), List.of())).thenReturn(response);

        AuthorizedResult<LostItemResponse> result = api.create("access", "refresh", request(), List.of());

        assertThat(result.body()).isEqualTo(response);
        assertThat(result.newAccessToken()).isEqualTo("new-access");
        assertThat(result.newRefreshToken()).isEqualTo("new-refresh");
    }

    @Test
    void boothManagerCannotManageLostItems() {
        authenticateAs(FestivalRole.BOOTH_MANAGER, "부스 매니저", "booth01", null, null);

        assertThatThrownBy(() -> api.create("access", "refresh", request(), List.of()))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.status().value()).isEqualTo(403);
                    assertThat(exception.code()).isEqualTo("LOST_ITEM_MANAGE_FORBIDDEN");
                });
        verify(lostItems, never()).createAs(any(), any(), any(), any());
    }

    @Test
    void loginIdIsUsedWhenStaffNameIsMissing() {
        authenticateAs(FestivalRole.ADMIN, null, "admin01", null, null);
        when(lostItems.createAs(USER_UUID, "admin01", request(), List.of())).thenReturn(response());

        api.create("access", "refresh", request(), List.of());

        verify(lostItems).createAs(USER_UUID, "admin01", request(), List.of());
    }

    private void authenticateAs(FestivalRole role, String name, String loginId,
                                String newAccessToken, String newRefreshToken) {
        MeResponse me = new MeResponse(USER_UUID, loginId, "user@example.com", "USER", "ACTIVE",
                name, null, null, null, null, null, null, null, null, Set.of(role));
        when(users.getMe("access", "refresh"))
                .thenReturn(new AuthorizedResult<>(me, newAccessToken, newRefreshToken));
    }

    private LostItemMutationRequest request() {
        return new LostItemMutationRequest("검은색 지갑", "학생회관 앞에서 발견했습니다.",
                LostItemStatus.HOLDING, false);
    }

    private LostItemResponse response() {
        Instant now = Instant.parse("2026-08-13T03:00:00Z");
        return new LostItemResponse(7L, "검은색 지갑", "학생회관 앞에서 발견했습니다.",
                LostItemStatus.HOLDING, "보관 중", false, 0, List.of(), "축제 스태프", now, now);
    }
}
