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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.syu_likelion.Festa_2026.admin.AdminAccessService.AdminIdentity;
import org.syu_likelion.Festa_2026.auth.AuthorizedSsoExecutor.AuthorizedResult;
import org.syu_likelion.Festa_2026.error.ApiException;
import org.syu_likelion.Festa_2026.notice.NoticeDtos.NoticeResponse;
import org.syu_likelion.Festa_2026.notice.NoticeService;
import org.syu_likelion.Festa_2026.notice.NoticeSort;
import org.syu_likelion.Festa_2026.user.FestivalRole;

@Controller
@RequestMapping("/admin/notices")
public class AdminNoticePageController {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final AdminAccessService adminAccess;
    private final AdminCookieManager cookies;
    private final NoticeService notices;

    public AdminNoticePageController(AdminAccessService adminAccess, AdminCookieManager cookies,
                                       NoticeService notices) {
        this.adminAccess = adminAccess;
        this.cookies = cookies;
        this.notices = notices;
    }

    @GetMapping
    String list(@RequestParam(defaultValue = "NEWEST") NoticeSort sort,
                @RequestParam(defaultValue = "0") int page,
                HttpServletRequest request, HttpServletResponse response, Model model) {
        AdminIdentity admin = staffOrNull(request, response);
        if (admin == null) return redirect(request);
        common(model, admin);
        var result = notices.listAdmin(sort, page, 20);
        model.addAttribute("result", result);
        model.addAttribute("notices", result.items());
        model.addAttribute("pageNumbers", AdminPagination.window(result.page(), result.totalPages()));
        model.addAttribute("selectedSort", sort);
        return "admin/notices/list";
    }

    @GetMapping("/new")
    String createPage(HttpServletRequest request, HttpServletResponse response, Model model) {
        AdminIdentity admin = staffOrNull(request, response);
        if (admin == null) return redirect(request);
        common(model, admin);
        model.addAttribute("form", NoticeAdminForm.empty());
        model.addAttribute("editing", false);
        return "admin/notices/form";
    }

    @PostMapping
    String create(@ModelAttribute("form") NoticeAdminForm form,
                  HttpServletRequest request, HttpServletResponse response, Model model,
                  RedirectAttributes redirectAttributes) {
        AdminIdentity admin = staffOrNull(request, response);
        if (admin == null) return redirect(request);
        try {
            notices.createAs(admin.userUuid(), admin.displayName(), form.toRequest(), form.getAttachmentFiles());
            redirectAttributes.addFlashAttribute("message", "일반 공지를 등록했습니다.");
            return "redirect:/admin/notices";
        } catch (ApiException exception) {
            common(model, admin);
            model.addAttribute("editing", false);
            model.addAttribute("error", exception.getMessage());
            return "admin/notices/form";
        }
    }

    @GetMapping("/{id}/edit")
    String editPage(@PathVariable Long id, HttpServletRequest request,
                    HttpServletResponse response, Model model) {
        AdminIdentity admin = staffOrNull(request, response);
        if (admin == null) return redirect(request);
        NoticeResponse notice = notices.getAdmin(id);
        common(model, admin);
        model.addAttribute("form", NoticeAdminForm.from(notice));
        model.addAttribute("notice", notice);
        model.addAttribute("editing", true);
        return "admin/notices/form";
    }

    @PostMapping("/{id}")
    String update(@PathVariable Long id, @ModelAttribute("form") NoticeAdminForm form,
                  HttpServletRequest request, HttpServletResponse response, Model model,
                  RedirectAttributes redirectAttributes) {
        AdminIdentity admin = staffOrNull(request, response);
        if (admin == null) return redirect(request);
        try {
            notices.updateAs(id, admin.userUuid(), form.toRequest(),
                    form.getRemoveAttachmentIds(), form.getAttachmentFiles());
            redirectAttributes.addFlashAttribute("message", "일반 공지를 수정했습니다.");
            return "redirect:/admin/notices";
        } catch (ApiException exception) {
            common(model, admin);
            model.addAttribute("notice", notices.getAdmin(id));
            model.addAttribute("editing", true);
            model.addAttribute("error", exception.getMessage());
            return "admin/notices/form";
        }
    }

    @PostMapping("/{id}/pin")
    String changePin(@PathVariable Long id, @RequestParam boolean pinned,
                     HttpServletRequest request, HttpServletResponse response,
                     RedirectAttributes redirectAttributes) {
        AdminIdentity admin = staffOrNull(request, response);
        if (admin == null) return redirect(request);
        notices.changePinnedAs(id, admin.userUuid(), pinned);
        redirectAttributes.addFlashAttribute("message", pinned ? "공지를 상단에 고정했습니다." : "상단 고정을 해제했습니다.");
        return "redirect:/admin/notices";
    }

    @PostMapping("/{id}/delete")
    String delete(@PathVariable Long id, HttpServletRequest request, HttpServletResponse response,
                  RedirectAttributes redirectAttributes) {
        AdminIdentity admin = staffOrNull(request, response);
        if (admin == null) return redirect(request);
        notices.deleteAs(id);
        redirectAttributes.addFlashAttribute("message", "일반 공지를 삭제했습니다.");
        return "redirect:/admin/notices";
    }

    private AdminIdentity staffOrNull(HttpServletRequest request, HttpServletResponse response) {
        try {
            AuthorizedResult<AdminIdentity> result = adminAccess.authenticate(
                    cookies.readAccessToken(request), cookies.readRefreshToken(request));
            cookies.applyRotation(response, result.newAccessToken(), result.newRefreshToken());
            FestivalRole role = result.body().role();
            return role == FestivalRole.STAFF || role == FestivalRole.ADMIN
                    || role == FestivalRole.SUPER_ADMIN ? result.body() : null;
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
        model.addAttribute("sortOptions", NoticeSort.values());
        model.addAttribute("seoulZone", SEOUL);
    }
}
