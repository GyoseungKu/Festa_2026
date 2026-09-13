package org.syu_likelion.Festa_2026.admin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.ResponseBody;
import org.syu_likelion.Festa_2026.admin.AdminAccessService.AdminIdentity;
import org.syu_likelion.Festa_2026.auth.AuthorizedSsoExecutor.AuthorizedResult;
import org.syu_likelion.Festa_2026.error.ApiException;
import org.syu_likelion.Festa_2026.monitoring.SystemMonitoringService;
import org.syu_likelion.Festa_2026.monitoring.SystemMonitoringService.SystemSnapshot;
import org.syu_likelion.Festa_2026.user.FestivalRole;

@Controller
public class AdminSystemPageController {
    private final AdminAccessService adminAccess;
    private final AdminCookieManager cookies;
    private final SystemMonitoringService monitoring;

    public AdminSystemPageController(AdminAccessService adminAccess, AdminCookieManager cookies,
                                     SystemMonitoringService monitoring) {
        this.adminAccess = adminAccess;
        this.cookies = cookies;
        this.monitoring = monitoring;
    }

    @GetMapping("/admin/system")
    String page(HttpServletRequest request, HttpServletResponse response, Model model) {
        AuthorizedResult<AdminIdentity> authenticated;
        try {
            authenticated = authenticate(request, response);
        } catch (RuntimeException invalidLogin) {
            cookies.clear(response);
            return "redirect:/admin/login";
        }
        if (authenticated.body().role() != FestivalRole.SUPER_ADMIN) return "redirect:/admin";
        model.addAttribute("adminName", authenticated.body().displayName());
        model.addAttribute("adminRole", authenticated.body().role()); model.addAttribute("adminNavigationRoles", authenticated.body().roles());
        return "admin/system";
    }

    @GetMapping(value = "/admin/system/snapshot", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    @Tag(name = "Admin Console", description = "관리자 웹 로그인 쿠키로 인증하는 운영 화면용 JSON API")
    @Operation(summary = "시스템 모니터링 스냅샷 조회", description = "관리자 웹 로그인 쿠키가 필요하며 SUPER_ADMIN만 사용할 수 있습니다. 애플리케이션, HTTP 요청, 데이터베이스 등 현재 모니터링 지표를 반환합니다.")
    ResponseEntity<SystemSnapshot> snapshot(HttpServletRequest request, HttpServletResponse response) {
        AuthorizedResult<AdminIdentity> authenticated = authenticate(request, response);
        if (authenticated.body().role() != FestivalRole.SUPER_ADMIN) {
            throw new ApiException(HttpStatus.FORBIDDEN, "SUPER_ADMIN_REQUIRED",
                    "시스템 모니터링은 최고 관리자만 사용할 수 있습니다.");
        }
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(monitoring.snapshot());
    }

    private AuthorizedResult<AdminIdentity> authenticate(HttpServletRequest request,
                                                         HttpServletResponse response) {
        AuthorizedResult<AdminIdentity> result = adminAccess.authenticate(
                cookies.readAccessToken(request), cookies.readRefreshToken(request));
        cookies.applyRotation(response, result.newAccessToken(), result.newRefreshToken());
        return result;
    }

}
