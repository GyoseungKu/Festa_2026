package org.syu_likelion.Festa_2026.auth;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.syu_likelion.Festa_2026.auth.AuthDtos.LoginRequest;
import org.syu_likelion.Festa_2026.auth.AuthDtos.SignupRequest;
import org.syu_likelion.Festa_2026.auth.AuthDtos.SignupResponse;
import org.syu_likelion.Festa_2026.auth.AuthDtos.TokenResponse;
import org.syu_likelion.Festa_2026.sso.SsoAuthClient;
import org.syu_likelion.Festa_2026.sso.SsoResult;
import org.syu_likelion.Festa_2026.user.FestivalRole;
import org.syu_likelion.Festa_2026.user.FestivalUserService;
import org.syu_likelion.Festa_2026.user.UserDtos.MeResponse;

class AuthServiceWelcomeEmailTests {
    private static final UUID USER_UUID = UUID.fromString("123e4567-e89b-12d3-a456-426614174403");
    private SsoAuthClient client;
    private FestivalUserService users;
    private WelcomeEmailService welcomeEmails;
    private AuthService service;

    @BeforeEach
    void setUp() {
        client = mock(SsoAuthClient.class);
        users = mock(FestivalUserService.class);
        welcomeEmails = mock(WelcomeEmailService.class);
        service = new AuthService(client, users, mock(AccountRecoveryAttemptLimiter.class), welcomeEmails);
    }

    @Test
    void signupQueuesWelcomeEmailUsingSubmittedProfile() {
        SignupRequest request = new SignupRequest("festival01", "password123", "new@example.com",
                "신규 사용자", null, "20260001", "컴퓨터공학과", null, null, null);
        when(client.register(request)).thenReturn(new SignupResponse(USER_UUID));

        service.signup(request);

        verify(users).linkAndGetRoles(USER_UUID);
        verify(welcomeEmails).sendLater(USER_UUID, "new@example.com", "신규 사용자");
    }

    @Test
    void firstOrPendingLoginQueuesWelcomeEmailUsingSsoProfile() {
        LoginRequest request = new LoginRequest("festival01", "password123");
        when(client.login(request)).thenReturn(new SsoResult<>(new TokenResponse("access-token"), "refresh-token"));
        when(client.getMe("access-token")).thenReturn(new MeResponse(USER_UUID, "festival01",
                "existing@example.com", "USER", "ACTIVE", "기존 SSO 사용자", null, "20260001",
                "컴퓨터공학과", 1, "ENROLLED", null, null, null, Set.of(FestivalRole.USER)));

        service.login(request);

        verify(users).linkAndGetRoles(USER_UUID);
        verify(welcomeEmails).sendLater(USER_UUID, "existing@example.com", "기존 SSO 사용자");
    }
}
