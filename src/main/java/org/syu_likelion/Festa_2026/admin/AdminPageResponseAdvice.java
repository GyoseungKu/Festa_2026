package org.syu_likelion.Festa_2026.admin;

import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** Applies a consistent Korean UTF-8 response context to server-rendered admin pages. */
@ControllerAdvice(basePackages = "org.syu_likelion.Festa_2026.admin")
public class AdminPageResponseAdvice {
    @ModelAttribute("adminCurrentPath")
    String currentPath(jakarta.servlet.http.HttpServletRequest request) {
        return request.getRequestURI().substring(request.getContextPath().length());
    }

    @ModelAttribute
    void configureResponse(HttpServletResponse response) {
        response.setLocale(Locale.KOREAN);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    }
}
