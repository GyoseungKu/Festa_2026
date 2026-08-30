package org.syu_likelion.Festa_2026.schoolsso;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
public class SchoolSubjectHasher {
    private final byte[] secret;

    public SchoolSubjectHasher(SchoolSsoProperties properties) {
        String configured = properties.subjectHashSecret();
        if (configured == null || configured.isBlank()) configured = properties.clientSecret();
        this.secret = configured == null ? new byte[0] : configured.getBytes(StandardCharsets.UTF_8);
    }

    public String hash(String schoolSubject) {
        if (schoolSubject == null || schoolSubject.isBlank() || secret.length < 16) {
            throw new IllegalStateException("School subject hash secret must contain at least 16 bytes");
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(schoolSubject.strip().getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Could not hash school subject", exception);
        }
    }
}
