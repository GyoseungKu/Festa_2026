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
    private static final String ACCOUNT_NAME = PREFIX + ".accountName";
    private static final String ACCOUNT_STUDENT_NO = PREFIX + ".accountStudentNo";
    private static final String ACCOUNT_DEPARTMENT = PREFIX + ".accountDepartment";
    private static final String PROFILE_USER_UUID = PREFIX + ".profileUserUuid";
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

    public void saveAccountState(HttpServletRequest request, String state, UUID userUuid,
                                 String name, String studentNo, String department) {
        saveAuthorization(request, state, AuthorizationFlow.ACCOUNT_VERIFICATION, userUuid);
        HttpSession session = request.getSession(true);
        session.setAttribute(ACCOUNT_NAME, name);
        session.setAttribute(ACCOUNT_STUDENT_NO, studentNo);
        session.setAttribute(ACCOUNT_DEPARTMENT, department);
    }

    private void saveAuthorization(HttpServletRequest request, String state, AuthorizationFlow flow, UUID userUuid) {
        HttpSession session = request.getSession(true);
        session.setAttribute(STATE, state);
        session.setAttribute(STATE_ISSUED_AT, clock.instant());
        session.setAttribute(FLOW, flow);
        if (userUuid == null) session.removeAttribute(USER_UUID);
        else session.setAttribute(USER_UUID, userUuid);
        session.removeAttribute(PROFILE);
        session.removeAttribute(PROFILE_USER_UUID);
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
        Object nameValue = session == null ? null : session.getAttribute(ACCOUNT_NAME);
        Object studentNoValue = session == null ? null : session.getAttribute(ACCOUNT_STUDENT_NO);
        Object departmentValue = session == null ? null : session.getAttribute(ACCOUNT_DEPARTMENT);
        if (session != null) {
            session.removeAttribute(STATE);
            session.removeAttribute(STATE_ISSUED_AT);
            session.removeAttribute(FLOW);
            session.removeAttribute(USER_UUID);
            session.removeAttribute(ACCOUNT_NAME);
            session.removeAttribute(ACCOUNT_STUDENT_NO);
            session.removeAttribute(ACCOUNT_DEPARTMENT);
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
        return new PendingAuthorization(flow, userUuid, stringValue(nameValue), stringValue(studentNoValue),
                stringValue(departmentValue));
    }

    public void saveProfile(HttpServletRequest request, SchoolAcademicProfile profile) {
        request.getSession(true).setAttribute(PROFILE, profile);
    }

    public void saveAccountProfile(HttpServletRequest request, UUID userUuid, SchoolAcademicProfile profile) {
        HttpSession session = request.getSession(true);
        session.setAttribute(PROFILE, profile);
        session.setAttribute(PROFILE_USER_UUID, userUuid);
    }

    public SchoolAcademicProfile requireAccountProfile(HttpServletRequest request, UUID userUuid) {
        HttpSession session = request.getSession(false);
        Object owner = session == null ? null : session.getAttribute(PROFILE_USER_UUID);
        if (!userUuid.equals(owner)) throw missingProfile();
        return requireProfile(request);
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
        if (session != null) session.removeAttribute(PROFILE_USER_UUID);
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
    public record PendingAuthorization(AuthorizationFlow flow, UUID userUuid, String name,
                                       String studentNo, String department) { }

    private String stringValue(Object value) { return value instanceof String text ? text : null; }
}
