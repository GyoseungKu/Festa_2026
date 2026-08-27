package org.syu_likelion.Festa_2026.temporaryauth;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.syu_likelion.Festa_2026.schoolsso.SchoolSsoProperties;

/**
 * Temporary same-origin frontend for Festa login/signup.
 * Delete this class, templates/temporary-auth.html, its test, and the
 * temporary-auth-ui.enabled property after the production frontend is ready.
 */
@Controller
@ConditionalOnProperty(name = "temporary-auth-ui.enabled", havingValue = "true")
public class TemporaryAuthUiController {
    private final SchoolSsoProperties schoolSso;

    public TemporaryAuthUiController(SchoolSsoProperties schoolSso) {
        this.schoolSso = schoolSso;
    }

    @GetMapping({"/temporary-auth", "/syu-sso-test"})
    String page(HttpServletResponse response, Model model) {
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Pragma", "no-cache");
        response.setHeader("Referrer-Policy", "no-referrer");
        response.setHeader("X-Robots-Tag", "noindex, nofollow");
        model.addAttribute("schoolSsoEnabled", schoolSso.enabled());
        model.addAttribute("schoolClientId", schoolSso.clientId());
        model.addAttribute("schoolAuthorizeUrl", schoolSso.authorizeUrl());
        model.addAttribute("schoolCallbackUrl", schoolSso.callbackUrl());
        return "temporary-auth";
    }
}
