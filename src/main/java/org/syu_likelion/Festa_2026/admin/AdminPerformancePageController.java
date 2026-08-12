package org.syu_likelion.Festa_2026.admin;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.ZoneId;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.syu_likelion.Festa_2026.admin.AdminAccessService.AdminIdentity;
import org.syu_likelion.Festa_2026.auth.AuthorizedSsoExecutor.AuthorizedResult;
import org.syu_likelion.Festa_2026.error.ApiException;
import org.syu_likelion.Festa_2026.performance.PerformanceCategory;
import org.syu_likelion.Festa_2026.performance.PerformanceDtos.PerformanceResponse;
import org.syu_likelion.Festa_2026.performance.PerformanceService;
import org.syu_likelion.Festa_2026.user.FestivalRole;

@Controller
@RequestMapping("/admin/performances")
public class AdminPerformancePageController {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final AdminAccessService adminAccess;
    private final AdminCookieManager cookies;
    private final PerformanceService performances;

    public AdminPerformancePageController(AdminAccessService adminAccess, AdminCookieManager cookies,
                                          PerformanceService performances) {
        this.adminAccess = adminAccess;
        this.cookies = cookies;
        this.performances = performances;
    }

    @GetMapping
    String list(HttpServletRequest request, HttpServletResponse response, Model model) {
        AdminIdentity admin = managerOrNull(request, response);
        if (admin == null) return redirect(request);
        common(model, admin);
        model.addAttribute("performances", performances.listAll());
        return "admin/performances/list";
    }

    @GetMapping("/new")
    String createPage(HttpServletRequest request, HttpServletResponse response, Model model) {
        AdminIdentity admin = managerOrNull(request, response);
        if (admin == null) return redirect(request);
        common(model, admin);
        model.addAttribute("form", PerformanceAdminForm.empty());
        model.addAttribute("editing", false);
        return "admin/performances/form";
    }

    @PostMapping
    String create(@ModelAttribute("form") PerformanceAdminForm form,
                  HttpServletRequest request, HttpServletResponse response, Model model,
                  RedirectAttributes redirectAttributes) {
        AdminIdentity admin = managerOrNull(request, response);
        if (admin == null) return redirect(request);
        try {
            performances.createAs(admin.userUuid(), form.toRequest(), form.getImageFiles(), form.getVideoFiles());
            redirectAttributes.addFlashAttribute("message", "공연팀을 등록했습니다.");
            return "redirect:/admin/performances";
        } catch (ApiException exception) {
            common(model, admin);
            model.addAttribute("editing", false);
            model.addAttribute("error", exception.getMessage());
            return "admin/performances/form";
        }
    }

    @GetMapping("/{id}/edit")
    String editPage(@PathVariable Long id, HttpServletRequest request,
                    HttpServletResponse response, Model model) {
        AdminIdentity admin = managerOrNull(request, response);
        if (admin == null) return redirect(request);
        PerformanceResponse performance = performances.getAdmin(id);
        common(model, admin);
        model.addAttribute("form", PerformanceAdminForm.from(performance));
        model.addAttribute("performance", performance);
        model.addAttribute("editing", true);
        return "admin/performances/form";
    }

    @PostMapping("/{id}")
    String update(@PathVariable Long id, @ModelAttribute("form") PerformanceAdminForm form,
                  HttpServletRequest request, HttpServletResponse response, Model model,
                  RedirectAttributes redirectAttributes) {
        AdminIdentity admin = managerOrNull(request, response);
        if (admin == null) return redirect(request);
        try {
            performances.updateAs(id, admin.userUuid(), form.toRequest(), form.getRemoveMediaIds(),
                    form.getImageFiles(), form.getVideoFiles());
            redirectAttributes.addFlashAttribute("message", "공연팀 정보를 수정했습니다.");
            return "redirect:/admin/performances";
        } catch (ApiException exception) {
            common(model, admin);
            model.addAttribute("performance", performances.getAdmin(id));
            model.addAttribute("editing", true);
            model.addAttribute("error", exception.getMessage());
            return "admin/performances/form";
        }
    }

    @PostMapping("/{id}/delete")
    String delete(@PathVariable Long id, HttpServletRequest request, HttpServletResponse response,
                  RedirectAttributes redirectAttributes) {
        AdminIdentity admin = managerOrNull(request, response);
        if (admin == null) return redirect(request);
        performances.deleteAs(id);
        redirectAttributes.addFlashAttribute("message", "공연팀을 삭제했습니다.");
        return "redirect:/admin/performances";
    }

    private AdminIdentity managerOrNull(HttpServletRequest request, HttpServletResponse response) {
        try {
            AuthorizedResult<AdminIdentity> result = adminAccess.authenticate(
                    cookies.readAccessToken(request), cookies.readRefreshToken(request));
            cookies.applyRotation(response, result.newAccessToken(), result.newRefreshToken());
            FestivalRole role = result.body().role();
            return role == FestivalRole.ADMIN || role == FestivalRole.SUPER_ADMIN ? result.body() : null;
        } catch (RuntimeException invalidLogin) {
            cookies.clear(response);
            return null;
        }
    }

    private String redirect(HttpServletRequest request) {
        return cookies.readAccessToken(request) == null ? "redirect:/admin/login" : "redirect:/admin";
    }

    private void common(Model model, AdminIdentity admin) {
        model.addAttribute("adminName", admin.displayName());
        model.addAttribute("adminRole", admin.role());
        model.addAttribute("categories", PerformanceCategory.values());
        model.addAttribute("seoulZone", SEOUL);
    }
}
