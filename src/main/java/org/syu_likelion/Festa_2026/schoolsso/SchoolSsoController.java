package org.syu_likelion.Festa_2026.schoolsso;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.util.UriComponentsBuilder;
import org.syu_likelion.Festa_2026.error.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Controller
public class SchoolSsoController {
    private static final Logger log = LoggerFactory.getLogger(SchoolSsoController.class);
    private final SecureRandom random = new SecureRandom();
    private final SchoolSsoProperties properties;
    private final SchoolSsoClient client;
    private final SchoolSsoSessionStore sessions;

    public SchoolSsoController(SchoolSsoProperties properties, SchoolSsoClient client,
                               SchoolSsoSessionStore sessions) {
        this.properties = properties;
        this.client = client;
        this.sessions = sessions;
    }

    @GetMapping("/api/auth/school/authorize")
    String authorize(HttpServletRequest request, HttpServletResponse response) {
        noStore(response);
        client.requireConfigured();
        byte[] randomBytes = new byte[32];
        random.nextBytes(randomBytes);
        String state = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        sessions.saveState(request, state);
        String location = UriComponentsBuilder.fromUriString(properties.authorizeUrl())
                .queryParam("client_id", properties.clientId())
                .queryParam("redirect_uri", properties.callbackUrl())
                .queryParam("state", state)
                .build().encode().toUriString();
        return "redirect:" + location;
    }

    @GetMapping("/auth/sso/callback")
    String callback(@RequestParam(required = false) String code,
                    @RequestParam(required = false) String state,
                    @RequestParam(required = false) String error,
                    HttpServletRequest request,
                    HttpServletResponse response) {
        noStore(response);
        if (!sessions.consumeAndVerifyState(request, state)) return resultRedirect("invalid_state");
        if (error != null) return resultRedirect("access_denied".equals(error) ? "access_denied" : "failed");
        try {
            sessions.saveProfile(request, client.exchangeAndVerify(code));
            return resultRedirect("success");
        } catch (ApiException exception) {
            log.warn("School SSO callback failed code={} status={} success=false",
                    exception.code(), exception.status().value());
            return resultRedirect("failed");
        }
    }

    @GetMapping("/api/auth/school/profile")
    @ResponseBody
    ResponseEntity<AcademicProfileResponse> profile(HttpServletRequest request) {
        SchoolAcademicProfile profile = sessions.requireProfile(request);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new AcademicProfileResponse(profile.studentNo(), profile.department(),
                        profile.name(), profile.consentTarget(), profile.expiresAt()));
    }

    @DeleteMapping("/api/auth/school/profile")
    @ResponseBody
    ResponseEntity<Void> clear(HttpServletRequest request) {
        sessions.clearProfile(request);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    private String resultRedirect(String result) {
        String location = UriComponentsBuilder.fromUriString(properties.returnUrl())
                .queryParam("schoolSso", result).build().encode().toUriString();
        return "redirect:" + location;
    }

    private void noStore(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Pragma", "no-cache");
        response.setHeader("Referrer-Policy", "no-referrer");
    }

    public record AcademicProfileResponse(String studentNo, String department, String name,
                                          String consentTarget, java.time.Instant expiresAt) { }
}
