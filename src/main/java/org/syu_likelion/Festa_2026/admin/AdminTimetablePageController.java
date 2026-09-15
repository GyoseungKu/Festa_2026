package org.syu_likelion.Festa_2026.admin;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.ZoneId;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.syu_likelion.Festa_2026.admin.AdminAccessService.AdminIdentity;
import org.syu_likelion.Festa_2026.auth.AuthorizedSsoExecutor.AuthorizedResult;
import org.syu_likelion.Festa_2026.error.ApiException;
import org.syu_likelion.Festa_2026.timetable.TimetableService;
import org.syu_likelion.Festa_2026.timetable.TimetableDtos.ScheduleResponse;
import org.syu_likelion.Festa_2026.performance.PerformanceService;
import org.syu_likelion.Festa_2026.user.FestivalRole;

@Controller
@RequestMapping("/admin/timetable")
public class AdminTimetablePageController {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final AdminAccessService adminAccess;
    private final AdminCookieManager cookies;
    private final TimetableService timetable;
    private final PerformanceService performances;

    public AdminTimetablePageController(AdminAccessService adminAccess, AdminCookieManager cookies,
                                          TimetableService timetable, PerformanceService performances) {
        this.adminAccess = adminAccess;
        this.cookies = cookies;
        this.performances = performances;
        this.timetable = timetable;
    }

    @GetMapping
    String list(HttpServletRequest request, HttpServletResponse response, Model model) {
        AdminIdentity admin = managerOrNull(request, response);
        if (admin == null) return redirect(request);
        common(model, admin);
        model.addAttribute("schedules", timetable.listAll());
        return "admin/timetable/list";
    }

    @GetMapping("/new")
    String createPage(HttpServletRequest request, HttpServletResponse response, Model model) {
        AdminIdentity admin = managerOrNull(request, response);
        if (admin == null) return redirect(request);
        common(model, admin);
        model.addAttribute("form", TimetableAdminForm.empty());
        model.addAttribute("editing", false);
        return "admin/timetable/form";
    }

    @PostMapping
    String create(@ModelAttribute("form") TimetableAdminForm form, BindingResult binding,
                  HttpServletRequest request, HttpServletResponse response, Model model,
                  RedirectAttributes redirectAttributes) {
        AdminIdentity admin = managerOrNull(request, response);
        if (admin == null) return redirect(request);
        if (binding.hasErrors()) {
            common(model, admin);
            model.addAttribute("editing", false);
            model.addAttribute("error", "입력한 날짜와 공연팀을 확인해 주세요.");
            return "admin/timetable/form";
        }
        try {
            timetable.createAs(admin.userUuid(), form.toRequest());
            redirectAttributes.addFlashAttribute("message", "일정을 등록했습니다.");
            return "redirect:/admin/timetable";
        } catch (ApiException exception) {
            common(model, admin);
            model.addAttribute("editing", false);
            model.addAttribute("error", exception.getMessage());
            return "admin/timetable/form";
        }
    }

    @GetMapping("/{id}/edit")
    String editPage(@PathVariable Long id, HttpServletRequest request,
                    HttpServletResponse response, Model model) {
        AdminIdentity admin = managerOrNull(request, response);
        if (admin == null) return redirect(request);
        ScheduleResponse schedule = timetable.getAdmin(id);
        common(model, admin);
        model.addAttribute("form", TimetableAdminForm.from(schedule));
        model.addAttribute("schedule", schedule);
        model.addAttribute("editing", true);
        return "admin/timetable/form";
    }

    @PostMapping("/{id}")
    String update(@PathVariable Long id, @ModelAttribute("form") TimetableAdminForm form, BindingResult binding,
                  HttpServletRequest request, HttpServletResponse response, Model model,
                  RedirectAttributes redirectAttributes) {
        AdminIdentity admin = managerOrNull(request, response);
        if (admin == null) return redirect(request);
        if (binding.hasErrors()) {
            common(model, admin);
            model.addAttribute("schedule", timetable.getAdmin(id));
            model.addAttribute("editing", true);
            model.addAttribute("error", "입력한 날짜와 공연팀을 확인해 주세요.");
            return "admin/timetable/form";
        }
        try {
            timetable.updateAs(id, admin.userUuid(), form.toRequest());
            redirectAttributes.addFlashAttribute("message", "일정 정보를 수정했습니다.");
            return "redirect:/admin/timetable";
        } catch (ApiException exception) {
            common(model, admin);
            model.addAttribute("schedule", timetable.getAdmin(id));
            model.addAttribute("editing", true);
            model.addAttribute("error", exception.getMessage());
            return "admin/timetable/form";
        }
    }

    @PostMapping("/{id}/delete")
    String delete(@PathVariable Long id, HttpServletRequest request, HttpServletResponse response,
                  RedirectAttributes redirectAttributes) {
        AdminIdentity admin = managerOrNull(request, response);
        if (admin == null) return redirect(request);
        timetable.deleteAs(id);
        redirectAttributes.addFlashAttribute("message", "일정을 삭제했습니다.");
        return "redirect:/admin/timetable";
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
        model.addAttribute("adminRole", admin.role()); model.addAttribute("adminNavigationRoles", admin.roles());
        model.addAttribute("performances", performances.listAll());
        model.addAttribute("seoulZone", SEOUL);
    }
}
