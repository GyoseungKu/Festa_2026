package org.syu_likelion.Festa_2026.user;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.junit.jupiter.api.Test;
import org.syu_likelion.Festa_2026.config.SsoProperties;
import org.syu_likelion.Festa_2026.sso.SsoInternalProfileClient;
import org.syu_likelion.Festa_2026.sso.SsoServiceTokenProvider;
import tools.jackson.databind.ObjectMapper;

class DeletedUserProfileTests {
    @Test void deletedIdentityNeverRequestsAnSsoProfile() {
        SsoProperties properties = mock(SsoProperties.class);
        when(properties.connectTimeout()).thenReturn(java.time.Duration.ofSeconds(1));
        SsoServiceTokenProvider tokens = mock(SsoServiceTokenProvider.class);
        var client = new SsoInternalProfileClient(properties, tokens, new ObjectMapper());
        var id = DeletedUserIdentity.create();
        var profile = client.getProfile(id);
        assertThat(profile.name()).isEqualTo("알 수 없음");
        assertThat(profile.email()).isNull();
        assertThat(profile.studentNo()).isNull();
        verifyNoInteractions(tokens);
    }
}
