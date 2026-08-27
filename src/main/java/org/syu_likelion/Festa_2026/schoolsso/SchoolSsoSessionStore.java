package org.syu_likelion.Festa_2026.schoolsso;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.syu_likelion.Festa_2026.error.ApiException;

@Component
public class SchoolSsoSessionStore {
    private static final String PREFIX = SchoolSsoSessionStore.class.getName();
    private static final String STATE = PREFIX + ".state";
    private static final String STATE_ISSUED_AT = PREFIX + ".stateIssuedAt";
    private static final String PROFILE = PREFIX + ".profile";
    private static final int MAX_STATE_LENGTH = 256;

    private final Clock clock;
    private final SchoolSsoProperties properties;

    public SchoolSsoSessionStore(Clock clock, SchoolSsoProperties properties) {
        this.clock = clock;
        this.properties = properties;
    }

    public void saveState(HttpServletRequest request, String state) {
        HttpSession session = request.getSession(true);
        session.setAttribute(STATE, state);
        session.setAttribute(STATE_ISSUED_AT, clock.instant());
        session.removeAttribute(PROFILE);
    }

    public boolean consumeAndVerifyState(HttpServletRequest request, String received) {
        HttpSession session = request.getSession(false);
        Object expectedValue = session == null ? null : session.getAttribute(STATE);
        Object issuedAtValue = session == null ? null : session.getAttribute(STATE_ISSUED_AT);
        if (session != null) {
            session.removeAttribute(STATE);
            session.removeAttribute(STATE_ISSUED_AT);
        }
        if (!(expectedValue instanceof String expected) || !(issuedAtValue instanceof Instant issuedAt)
                || received == null || expected.length() > MAX_STATE_LENGTH || received.length() > MAX_STATE_LENGTH
                || !clock.instant().isBefore(issuedAt.plus(properties.stateTtl()))) return false;
        byte[] left = expected.getBytes(StandardCharsets.UTF_8);
        byte[] right = received.getBytes(StandardCharsets.UTF_8);
        return left.length == right.length && MessageDigest.isEqual(left, right);
    }

    public void saveProfile(HttpServletRequest request, SchoolAcademicProfile profile) {
        request.getSession(true).setAttribute(PROFILE, profile);
    }

    public SchoolAcademicProfile requireProfile(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        Object value = session == null ? null : session.getAttribute(PROFILE);
        if (!(value instanceof SchoolAcademicProfile profile)) throw missingProfile();
        if (!clock.instant().isBefore(profile.expiresAt())) {
            session.removeAttribute(PROFILE);
            throw missingProfile();
        }
        return profile;
    }

    public void clearProfile(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) session.removeAttribute(PROFILE);
    }

    private ApiException missingProfile() {
        return new ApiException(HttpStatus.BAD_REQUEST, "SCHOOL_SSO_VERIFICATION_REQUIRED",
                "학교 SSO 학적정보 인증을 먼저 완료해 주세요.");
    }
}
