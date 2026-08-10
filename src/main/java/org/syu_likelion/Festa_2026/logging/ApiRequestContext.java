package org.syu_likelion.Festa_2026.logging;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

public final class ApiRequestContext {
    static final String REQUEST_ID_ATTRIBUTE = ApiRequestContext.class.getName() + ".requestId";
    static final String USER_UUID_ATTRIBUTE = ApiRequestContext.class.getName() + ".userUuid";

    private ApiRequestContext() { }

    public static void markAuthenticatedUser(UUID userUuid) {
        if (userUuid == null) return;
        currentRequest().ifPresent(request -> {
            request.setAttribute(USER_UUID_ATTRIBUTE, userUuid);
            MDC.put("userUuid", userUuid.toString());
        });
    }

    public static Optional<String> currentRequestId() {
        return currentRequest().map(request -> request.getAttribute(REQUEST_ID_ATTRIBUTE))
                .filter(String.class::isInstance).map(String.class::cast);
    }

    static UUID authenticatedUser(HttpServletRequest request) {
        Object value = request.getAttribute(USER_UUID_ATTRIBUTE);
        return value instanceof UUID uuid ? uuid : null;
    }

    private static Optional<HttpServletRequest> currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return Optional.of(attributes.getRequest());
        }
        return Optional.empty();
    }
}
