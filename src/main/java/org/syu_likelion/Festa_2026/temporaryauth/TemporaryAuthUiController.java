package org.syu_likelion.Festa_2026.temporaryauth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.syu_likelion.Festa_2026.error.ApiException;
import org.syu_likelion.Festa_2026.schoolsso.SchoolSsoClient;
import org.syu_likelion.Festa_2026.schoolsso.SchoolSsoProperties;
import org.syu_likelion.Festa_2026.schoolsso.SchoolSsoSessionStore;

/** Temporary same-origin school authentication UI; disable when the frontend owns this route. */
@Controller
@ConditionalOnProperty(name = "temporary-auth-ui.enabled", havingValue = "true")
public class TemporaryAuthUiController {
    private final SchoolSsoProperties properties;
    private final SchoolSsoClient client;
    private final SchoolSsoSessionStore sessions;

    public TemporaryAuthUiController(SchoolSsoProperties properties, SchoolSsoClient client,
                                     SchoolSsoSessionStore sessions) {
        this.properties = properties;
        this.client = client;
        this.sessions = sessions;
    }

    @GetMapping({"/temporary-auth", "/syu-sso-test"})
    String page(@RequestParam(required = false) String schoolSso,
                @RequestParam(required = false) String schoolVerification,
                HttpServletRequest request, HttpServletResponse response, Model model) {
        noStore(response);
        boolean configured = true;
        try {
            client.requireConfigured();
        } catch (ApiException unavailable) {
            configured = false;
            model.addAttribute("configurationError", unavailable.getMessage());
        }
        model.addAttribute("configured", configured);
        model.addAttribute("schoolClientId", properties.clientId());
        model.addAttribute("schoolAuthorizeUrl", properties.authorizeUrl());
        model.addAttribute("schoolCallbackUrl", properties.callbackUrl());
        model.addAttribute("schoolReturnUrl", properties.returnUrl());
        if (schoolSso != null) {
            model.addAttribute("resultMessage", signupMessage(schoolSso));
        } else if (schoolVerification != null) {
            model.addAttribute("resultMessage", accountMessage(schoolVerification));
        }
        // Never treat a success query parameter as proof: only show this browser's verified session profile.
        try {
            model.addAttribute("profile", sessions.requireProfile(request));
        } catch (ApiException missing) {
            if ("success".equals(schoolSso)) {
                model.addAttribute("resultMessage", "학적정보가 없거나 만료되었습니다. 같은 브라우저에서 학교 인증을 다시 시작해 주세요.");
            }
        }
        return "temporary-auth";
    }

    @PostMapping("/temporary-auth/clear")
    String clear(HttpServletRequest request, HttpServletResponse response) {
        noStore(response);
        sessions.clearProfile(request);
        return "redirect:/temporary-auth";
    }

    private String signupMessage(String result) {
        return switch (result) {
            case "success" -> "학교 인증이 완료되어 학적정보를 받았습니다.";
            case "invalid_state" -> "인증 요청이 만료되었거나 브라우저 세션이 일치하지 않습니다. 처음부터 다시 시도해 주세요.";
            case "access_denied" -> "학교 정보 제공에 동의하지 않아 인증을 취소했습니다.";
            default -> "학교 인증을 완료하지 못했습니다. 서버의 학교 SSO 오류 로그를 확인해 주세요.";
        };
    }

    private String accountMessage(String result) {
        return switch (result) {
            case "success" -> "기존 계정의 학생 인증 처리가 완료되었습니다.";
            case "pending_approval" -> "학교 정보와 회원정보가 달라 관리자 승인 대기 상태입니다.";
            case "department_update_required" -> "학교 학과와 회원 학과가 다릅니다. 학과 변경 확인 API에서 계속 진행해 주세요.";
            case "already_linked" -> "이 학번은 이미 다른 계정에 연결되어 있습니다.";
            default -> signupMessage(result);
        };
    }

    private void noStore(HttpServletResponse response) {
        response.setLocale(Locale.KOREAN);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Pragma", "no-cache");
        response.setHeader("Referrer-Policy", "no-referrer");
        response.setHeader("X-Robots-Tag", "noindex, nofollow");
    }
}
