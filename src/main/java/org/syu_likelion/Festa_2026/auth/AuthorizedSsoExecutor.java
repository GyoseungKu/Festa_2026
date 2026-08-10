package org.syu_likelion.Festa_2026.auth;

import org.springframework.stereotype.Component;
import org.syu_likelion.Festa_2026.sso.SsoException;
import org.syu_likelion.Festa_2026.sso.SsoResult;
import org.syu_likelion.Festa_2026.auth.AuthDtos.TokenResponse;

@Component
public class AuthorizedSsoExecutor {
    private final TokenRefreshCoordinator refreshCoordinator;

    public AuthorizedSsoExecutor(TokenRefreshCoordinator refreshCoordinator) {
        this.refreshCoordinator = refreshCoordinator;
    }

    public <T> AuthorizedResult<T> execute(String accessToken, String refreshToken, AuthorizedCall<T> call) {
        try {
            return new AuthorizedResult<>(call.invoke(accessToken), null, null);
        } catch (SsoException exception) {
            if (exception.statusCode() != 401) throw exception;
            SsoResult<TokenResponse> refreshed = refreshCoordinator.refresh(refreshToken);
            T body = call.invoke(refreshed.body().accessToken());
            return new AuthorizedResult<>(body, refreshed.body().accessToken(), refreshed.refreshToken());
        }
    }

    @FunctionalInterface
    public interface AuthorizedCall<T> {
        T invoke(String accessToken);
    }

    public record AuthorizedResult<T>(T body, String newAccessToken, String newRefreshToken) { }
}
