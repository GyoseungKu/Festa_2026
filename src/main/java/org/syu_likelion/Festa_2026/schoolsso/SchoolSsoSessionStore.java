package org.syu_likelion.Festa_2026.schoolsso;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.syu_likelion.Festa_2026.error.ApiException;

@Component
public class SchoolSsoSessionStore {
    private static final String PREFIX = SchoolSsoSessionStore.class.getName();
    private static final String STATE = PREFIX + ".state";
    private static final String STATE_ISSUED_AT = PREFIX + ".stateIssuedAt";
    private static final String PROFILE = PREFIX + ".profile";
    private static final String FLOW = PREFIX + ".flow";
    private static final String USER_UUID = PREFIX + ".userUuid";
    private static final int MAX_STATE_LENGTH = 256;

    private final Clock clock;
    private final SchoolSsoProperties properties;

    public SchoolSsoSessionStore(Clock clock, SchoolSsoProperties properties) {
        this.clock = clock;
        this.properties = properties;
    }

    public void saveState(HttpServletRequest request, String state) {
        saveAuthorization(request, state, AuthorizationFlow.SIGNUP, null);
    }

    public void saveAccountState(HttpServletRequest request, String state, UUID userUuid) {
        saveAuthorization(request, state, AuthorizationFlow.ACCOUNT_VERIFICATION, userUuid);
    }

    private void saveAuthorization(HttpServletRequest request, String state, AuthorizationFlow flow, UUID userUuid) {
        HttpSession session = request.getSession(true);
        session.setAttribute(STATE, state);
        session.setAttribute(STATE_ISSUED_AT, clock.instant());
        session.setAttribute(FLOW, flow);
        if (userUuid == null) session.removeAttribute(USER_UUID);
        else session.setAttribute(USER_UUID, userUuid);
        session.removeAttribute(PROFILE);
    }

    public boolean consumeAndVerifyState(HttpServletRequest request, String received) {
        return consumeAuthorization(request, received) != null;
    }

    public PendingAuthorization consumeAuthorization(HttpServletRequest request, String received) {
        HttpSession session = request.getSession(false);
        Object expectedValue = session == null ? null : session.getAttribute(STATE);
        Object issuedAtValue = session == null ? null : session.getAttribute(STATE_ISSUED_AT);
        Object flowValue = session == null ? null : session.getAttribute(FLOW);
        Object userUuidValue = session == null ? null : session.getAttribute(USER_UUID);
        if (session != null) {
            session.removeAttribute(STATE);
            session.removeAttribute(STATE_ISSUED_AT);
            session.removeAttribute(FLOW);
            session.removeAttribute(USER_UUID);
        }
        if (!(expectedValue instanceof String expected) || !(issuedAtValue instanceof Instant issuedAt)
                || !(flowValue instanceof AuthorizationFlow flow)
                || received == null || expected.length() > MAX_STATE_LENGTH || received.length() > MAX_STATE_LENGTH
                || !clock.instant().isBefore(issuedAt.plus(properties.stateTtl()))) return null;
        byte[] left = expected.getBytes(StandardCharsets.UTF_8);
        byte[] right = received.getBytes(StandardCharsets.UTF_8);
        if (left.length != right.length || !MessageDigest.isEqual(left, right)) return null;
        UUID userUuid = userUuidValue instanceof UUID uuid ? uuid : null;
        if (flow == AuthorizationFlow.ACCOUNT_VERIFICATION && userUuid == null) return null;
        return new PendingAuthorization(flow, userUuid);
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

    public AuthorizationFlow currentFlow(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        Object value = session == null ? null : session.getAttribute(FLOW);
        return value instanceof AuthorizationFlow flow ? flow : null;
    }

    public enum AuthorizationFlow { SIGNUP, ACCOUNT_VERIFICATION }
    public record PendingAuthorization(AuthorizationFlow flow, UUID userUuid) { }
}
