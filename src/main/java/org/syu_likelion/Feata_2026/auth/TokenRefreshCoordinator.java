package org.syu_likelion.Feata_2026.auth;

import java.util.concurrent.locks.ReentrantLock;
import org.springframework.stereotype.Component;
import org.syu_likelion.Feata_2026.sso.SsoAuthClient;
import org.syu_likelion.Feata_2026.sso.SsoException;
import org.syu_likelion.Feata_2026.sso.SsoResult;
import org.syu_likelion.Feata_2026.auth.AuthDtos.TokenResponse;

@Component
public class TokenRefreshCoordinator {
    private final ReentrantLock[] locks = new ReentrantLock[64];
    private final SsoAuthClient client;

    public TokenRefreshCoordinator(SsoAuthClient client) {
        this.client = client;
        for (int i = 0; i < locks.length; i++) locks[i] = new ReentrantLock();
    }

    public SsoResult<TokenResponse> refresh(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) throw new SsoException(401, "Refresh token is missing");
        ReentrantLock lock = locks[(refreshToken.hashCode() & Integer.MAX_VALUE) % locks.length];
        lock.lock();
        try {
            SsoResult<TokenResponse> result = client.refresh(refreshToken);
            requireAccessToken(result.body());
            return result;
        } finally {
            lock.unlock();
        }
    }

    static void requireAccessToken(TokenResponse response) {
        if (response == null || response.accessToken() == null || response.accessToken().isBlank()) {
            throw new SsoException(502, "SSO response did not contain access token");
        }
    }
}
