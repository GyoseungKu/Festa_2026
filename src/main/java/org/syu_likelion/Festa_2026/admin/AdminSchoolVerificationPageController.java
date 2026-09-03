package org.syu_likelion.Festa_2026.admin;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.syu_likelion.Festa_2026.admin.AdminAccessService.AdminIdentity;
import org.syu_likelion.Festa_2026.auth.AuthorizedSsoExecutor.AuthorizedResult;
import org.syu_likelion.Festa_2026.error.ApiException;
import org.syu_likelion.Festa_2026.user.FestivalRole;
import org.syu_likelion.Festa_2026.user.SchoolVerificationApprovalService;

@Controller
public class AdminSchoolVerificationPageController {
    private final AdminAccessService adminAccess;
    private final AdminCookieManager cookies;
    private final SchoolVerificationApprovalService approvals;

    public AdminSchoolVerificationPageController(AdminAccessService adminAccess, AdminCookieManager cookies,
                                                 SchoolVerificationApprovalService approvals) {
        this.adminAccess = adminAccess;
        this.cookies = cookies;
        this.approvals = approvals;
    }

    @GetMapping("/admin/school-verifications")
    String page(HttpServletRequest request, HttpServletResponse response, Model model) {
        AuthorizedResult<AdminIdentity> authenticated;
        try {
            authenticated = authenticate(request, response);
        } catch (RuntimeException invalidLogin) {
            cookies.clear(response);
            return "redirect:/admin/login";
        }
        if (!authenticated.body().hasRole(FestivalRole.SUPER_ADMIN)) return "redirect:/admin";
        model.addAttribute("adminName", authenticated.body().displayName());
        model.addAttribute("adminRole", authenticated.body().role());
        model.addAttribute("verificationRequests", approvals.list());
        return "admin/school-verifications";
    }

    @PostMapping("/admin/school-verifications/{id}/approve")
    String approve(@PathVariable Long id, HttpServletRequest request, HttpServletResponse response,
                   RedirectAttributes flash) {
        requireSuperAdmin(request, response);
        approvals.approve(id);
        flash.addFlashAttribute("message", "학생 인증 요청을 승인했습니다.");
        return "redirect:/admin/school-verifications";
    }

    @PostMapping("/admin/school-verifications/{id}/delete")
    String delete(@PathVariable Long id, HttpServletRequest request, HttpServletResponse response,
                  RedirectAttributes flash) {
        requireSuperAdmin(request, response);
        approvals.delete(id);
        flash.addFlashAttribute("message", "학생 인증 요청을 삭제했습니다.");
        return "redirect:/admin/school-verifications";
    }

    private AuthorizedResult<AdminIdentity> authenticate(HttpServletRequest request, HttpServletResponse response) {
        AuthorizedResult<AdminIdentity> result = adminAccess.authenticate(
                cookies.readAccessToken(request), cookies.readRefreshToken(request));
        cookies.applyRotation(response, result.newAccessToken(), result.newRefreshToken());
        return result;
    }

    private void requireSuperAdmin(HttpServletRequest request, HttpServletResponse response) {
        if (!authenticate(request, response).body().hasRole(FestivalRole.SUPER_ADMIN)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "SUPER_ADMIN_REQUIRED",
                    "학생 인증 승인 요청은 최고 관리자만 처리할 수 있습니다.");
        }
    }
}
