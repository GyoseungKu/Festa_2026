package org.syu_likelion.Festa_2026.admin;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import java.util.List;
import java.util.UUID;
import org.syu_likelion.Festa_2026.admin.AdminAccessService.AdminIdentity;
import org.syu_likelion.Festa_2026.auth.AuthDtos.LoginRequest;
import org.syu_likelion.Festa_2026.auth.AuthService;
import org.syu_likelion.Festa_2026.auth.AuthorizedSsoExecutor.AuthorizedResult;
import org.syu_likelion.Festa_2026.error.ApiException;
import org.syu_likelion.Festa_2026.qr.QrService;
import org.syu_likelion.Festa_2026.user.FestivalRole;
import org.syu_likelion.Festa_2026.user.SchoolVerificationApprovalService;
import org.syu_likelion.Festa_2026.sso.SsoException;

@Controller
public class AdminPageController {
    private final AuthService auth;
    private final AdminAccessService adminAccess;
    private final AdminCookieManager cookies;
    private final QrService qrService;
    private final SchoolVerificationApprovalService schoolVerifications;

    public AdminPageController(AuthService auth, AdminAccessService adminAccess,
                               AdminCookieManager cookies, QrService qrService,
                               SchoolVerificationApprovalService schoolVerifications) {
        this.auth = auth;
        this.adminAccess = adminAccess;
        this.cookies = cookies;
        this.qrService = qrService;
        this.schoolVerifications = schoolVerifications;
    }

    @GetMapping("/admin/login")
    String loginPage(HttpServletRequest request, HttpServletResponse response) {
        if (cookies.readAccessToken(request) == null) return "admin/login";
        try {
            requireAdmin(request, response);
            return "redirect:/admin";
        } catch (RuntimeException invalidLogin) {
            cookies.clear(response);
            return "admin/login";
        }
    }

    @PostMapping("/admin/login")
    String login(@RequestParam String loginId, @RequestParam String password,
                 HttpServletResponse response, Model model) {
        if (loginId == null || loginId.isBlank() || password == null || password.isBlank()) {
            model.addAttribute("error", "아이디와 비밀번호를 입력해 주세요.");
            model.addAttribute("loginId", loginId);
            return "admin/login";
        }
        try {
            AuthService.LoginResult login = auth.login(new LoginRequest(loginId, password));
            adminAccess.authenticate(login.tokens().accessToken(), login.refreshToken());
            cookies.setLoginCookies(response, login.tokens().accessToken(), login.refreshToken());
            return "redirect:/admin";
        } catch (ApiException exception) {
            model.addAttribute("error", "관리자 페이지 접근 권한이 없습니다.");
        } catch (SsoException exception) {
            model.addAttribute("error", "아이디 또는 비밀번호를 확인해 주세요.");
        }
        model.addAttribute("loginId", loginId);
        return "admin/login";
    }

    @PostMapping("/admin/logout")
    String logout(HttpServletRequest request, HttpServletResponse response) {
        String accessToken = cookies.readAccessToken(request);
        if (accessToken != null && !accessToken.isBlank()) auth.logout(accessToken);
        cookies.clear(response);
        return "redirect:/admin/login";
    }

    @GetMapping("/admin")
    String dashboard(HttpServletRequest request, HttpServletResponse response, Model model) {
        AuthorizedResult<AdminIdentity> admin = authenticateOrNull(request, response);
        if (admin == null) return "redirect:/admin/login";
        addAdmin(model, admin.body());
        return "admin/dashboard";
    }

    @GetMapping("/admin/qr")
    String qrPage(HttpServletRequest request, HttpServletResponse response, Model model) {
        AuthorizedResult<AdminIdentity> admin = authenticateOrNull(request, response);
        if (admin == null) return "redirect:/admin/login";
        addAdmin(model, admin.body());
        return "admin/qr-scan";
    }

    @PostMapping("/admin/qr/scan")
    String scanQr(@RequestParam String token, HttpServletRequest request,
                  HttpServletResponse response, Model model) {
        AuthorizedResult<AdminIdentity> admin = authenticateOrNull(request, response);
        if (admin == null) return "redirect:/admin/login";
        addAdmin(model, admin.body());
        if (token == null || token.isBlank()) {
            model.addAttribute("error", "QR 토큰을 입력하거나 카메라로 스캔해 주세요.");
            return "admin/qr-scan";
        }
        try {
            model.addAttribute("result", qrService.scanAs(admin.body().role(), token.trim()));
        } catch (ApiException exception) {
            model.addAttribute("error", exception.getMessage());
        } catch (SsoException exception) {
            model.addAttribute("error", "SSO 사용자 정보를 조회하지 못했습니다. 잠시 후 다시 시도해 주세요.");
        }
        return "admin/qr-scan";
    }

    @PostMapping("/admin/qr/search")
    String searchUsers(@RequestParam String query, @RequestParam(defaultValue = "0") int page,
                       HttpServletRequest request,
                       HttpServletResponse response, Model model) {
        AuthorizedResult<AdminIdentity> admin = authenticateOrNull(request, response);
        if (admin == null) return "redirect:/admin/login";
        addAdmin(model, admin.body());
        model.addAttribute("searchQuery", query);
        try {
            addSearchResult(model, qrService.searchAs(admin.body().role(), query, page, 20));
        } catch (ApiException exception) {
            model.addAttribute("error", exception.getMessage());
        } catch (SsoException exception) {
            model.addAttribute("error", "SSO 사용자 정보를 조회하지 못했습니다. 잠시 후 다시 시도해 주세요.");
        }
        return "admin/qr-scan";
    }

    @PostMapping("/admin/qr/users/{userUuid}/role")
    String updateUserRole(@PathVariable UUID userUuid, @RequestParam FestivalRole managementRole,
                          @RequestParam(required = false) String query, HttpServletRequest request,
                          @RequestParam(defaultValue = "0") int page,
                          HttpServletResponse response, Model model) {
        AuthorizedResult<AdminIdentity> admin = authenticateOrNull(request, response);
        if (admin == null) return "redirect:/admin/login";
        addAdmin(model, admin.body());
        model.addAttribute("searchQuery", query);
        try {
            qrService.updateRoleAs(admin.body().userUuid(), admin.body().role(), userUuid, managementRole);
            model.addAttribute("message", "사용자 관리 권한을 변경했습니다.");
            if (query != null && !query.isBlank())
                addSearchResult(model, qrService.searchAs(admin.body().role(), query, page, 20));
        } catch (ApiException exception) {
            model.addAttribute("error", exception.getMessage());
            if (query != null && !query.isBlank()) {
                try { addSearchResult(model, qrService.searchAs(admin.body().role(), query, page, 20)); }
                catch (RuntimeException ignored) { /* 원래 권한 변경 오류를 우선 표시합니다. */ }
            }
        }
        return "admin/qr-scan";
    }

    private AuthorizedResult<AdminIdentity> authenticateOrNull(HttpServletRequest request,
                                                               HttpServletResponse response) {
        try {
            return requireAdmin(request, response);
        } catch (RuntimeException invalidLogin) {
            cookies.clear(response);
            return null;
        }
    }

    private AuthorizedResult<AdminIdentity> requireAdmin(HttpServletRequest request,
                                                         HttpServletResponse response) {
        AuthorizedResult<AdminIdentity> result = adminAccess.authenticate(
                cookies.readAccessToken(request), cookies.readRefreshToken(request));
        cookies.applyRotation(response, result.newAccessToken(), result.newRefreshToken());
        return result;
    }

    private void addAdmin(Model model, AdminIdentity admin) {
        model.addAttribute("adminName", admin.displayName());
        model.addAttribute("adminRole", admin.role());
        boolean superAdmin = admin.hasRole(org.syu_likelion.Festa_2026.user.FestivalRole.SUPER_ADMIN);
        boolean canManagePerformances = admin.hasRole(org.syu_likelion.Festa_2026.user.FestivalRole.ADMIN)
                || superAdmin;
        model.addAttribute("canManagePerformances", canManagePerformances);
        model.addAttribute("canManagePolls", canManagePerformances);
        boolean canManageStamps = canManagePerformances
                || admin.hasRole(org.syu_likelion.Festa_2026.user.FestivalRole.BOOTH_MANAGER);
        model.addAttribute("canManageStamps", canManageStamps);
        boolean canManageStaffFeatures = admin.hasRole(org.syu_likelion.Festa_2026.user.FestivalRole.STAFF)
                || canManagePerformances;
        model.addAttribute("canManageLostItems", canManageStaffFeatures);
        model.addAttribute("canManageBirthdayMessages", canManageStaffFeatures);
        model.addAttribute("canMonitorSystem", superAdmin);
        model.addAttribute("canManageSchoolVerifications", superAdmin);
        model.addAttribute("schoolVerificationRequestCount",
                superAdmin ? schoolVerifications.list().size() : 0);
        model.addAttribute("canManageUserRoles", canManagePerformances);
        model.addAttribute("managementRoleOptions", superAdmin
                ? List.of(FestivalRole.USER, FestivalRole.STAFF, FestivalRole.ADMIN, FestivalRole.SUPER_ADMIN)
                : canManagePerformances ? List.of(FestivalRole.USER, FestivalRole.STAFF) : List.of());
        model.addAttribute("availableFeatureCount", 1 + (canManageStaffFeatures ? 2 : 0)
                + (canManagePerformances ? 3 : 0) + (canManageStamps ? 1 : 0) + (superAdmin ? 2 : 0));
    }

    private void addSearchResult(Model model, org.syu_likelion.Festa_2026.qr.QrDtos.UserSearchResponse result) {
        model.addAttribute("searchResult", result);
        model.addAttribute("searchPages", AdminPagination.window(result.page(), result.totalPages()));
    }
}
