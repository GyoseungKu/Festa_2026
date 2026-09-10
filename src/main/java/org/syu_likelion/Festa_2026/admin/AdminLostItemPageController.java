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
import org.syu_likelion.Festa_2026.lostitem.LostItemDtos.LostItemResponse;
import org.syu_likelion.Festa_2026.lostitem.LostItemService;
import org.syu_likelion.Festa_2026.lostitem.LostItemSort;
import org.syu_likelion.Festa_2026.lostitem.LostItemStatus;
import org.syu_likelion.Festa_2026.user.FestivalRole;

@Controller
@RequestMapping("/admin/lost-items")
public class AdminLostItemPageController {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final AdminAccessService adminAccess;
    private final AdminCookieManager cookies;
    private final LostItemService lostItems;

    public AdminLostItemPageController(AdminAccessService adminAccess, AdminCookieManager cookies,
                                       LostItemService lostItems) {
        this.adminAccess = adminAccess;
        this.cookies = cookies;
        this.lostItems = lostItems;
    }

    @GetMapping
    String list(@RequestParam(required = false) LostItemStatus status,
                @RequestParam(defaultValue = "NEWEST") LostItemSort sort,
                @RequestParam(defaultValue = "0") int page,
                HttpServletRequest request, HttpServletResponse response, Model model) {
        AdminIdentity admin = staffOrNull(request, response);
        if (admin == null) return redirect(request);
        common(model, admin);
        var result = lostItems.listAdmin(status, sort, page, 20);
        model.addAttribute("result", result);
        model.addAttribute("notices", result.items());
        model.addAttribute("pageNumbers", AdminPagination.window(result.page(), result.totalPages()));
        model.addAttribute("selectedStatus", status);
        model.addAttribute("selectedSort", sort);
        return "admin/lost-items/list";
    }

    @GetMapping("/new")
    String createPage(HttpServletRequest request, HttpServletResponse response, Model model) {
        AdminIdentity admin = staffOrNull(request, response);
        if (admin == null) return redirect(request);
        common(model, admin);
        model.addAttribute("form", LostItemAdminForm.empty());
        model.addAttribute("editing", false);
        return "admin/lost-items/form";
    }

    @PostMapping
    String create(@ModelAttribute("form") LostItemAdminForm form,
                  HttpServletRequest request, HttpServletResponse response, Model model,
                  RedirectAttributes redirectAttributes) {
        AdminIdentity admin = staffOrNull(request, response);
        if (admin == null) return redirect(request);
        try {
            lostItems.createAs(admin.userUuid(), admin.displayName(), form.toRequest(), form.getImageFiles());
            redirectAttributes.addFlashAttribute("message", "분실물 공지를 등록했습니다.");
            return "redirect:/admin/lost-items";
        } catch (ApiException exception) {
            common(model, admin);
            model.addAttribute("editing", false);
            model.addAttribute("error", exception.getMessage());
            return "admin/lost-items/form";
        }
    }

    @GetMapping("/{id}/edit")
    String editPage(@PathVariable Long id, HttpServletRequest request,
                    HttpServletResponse response, Model model) {
        AdminIdentity admin = staffOrNull(request, response);
        if (admin == null) return redirect(request);
        LostItemResponse notice = lostItems.getAdmin(id);
        common(model, admin);
        model.addAttribute("form", LostItemAdminForm.from(notice));
        model.addAttribute("notice", notice);
        model.addAttribute("editing", true);
        return "admin/lost-items/form";
    }

    @PostMapping("/{id}")
    String update(@PathVariable Long id, @ModelAttribute("form") LostItemAdminForm form,
                  HttpServletRequest request, HttpServletResponse response, Model model,
                  RedirectAttributes redirectAttributes) {
        AdminIdentity admin = staffOrNull(request, response);
        if (admin == null) return redirect(request);
        try {
            lostItems.updateAs(id, admin.userUuid(), form.toRequest(),
                    form.getRemoveImageIds(), form.getImageFiles());
            redirectAttributes.addFlashAttribute("message", "분실물 공지를 수정했습니다.");
            return "redirect:/admin/lost-items";
        } catch (ApiException exception) {
            common(model, admin);
            model.addAttribute("notice", lostItems.getAdmin(id));
            model.addAttribute("editing", true);
            model.addAttribute("error", exception.getMessage());
            return "admin/lost-items/form";
        }
    }

    @PostMapping("/{id}/status")
    String changeStatus(@PathVariable Long id, @RequestParam LostItemStatus status,
                        HttpServletRequest request, HttpServletResponse response,
                        RedirectAttributes redirectAttributes) {
        AdminIdentity admin = staffOrNull(request, response);
        if (admin == null) return redirect(request);
        lostItems.changeStatusAs(id, admin.userUuid(), status);
        redirectAttributes.addFlashAttribute("message", "반환 상태를 변경했습니다.");
        return "redirect:/admin/lost-items";
    }

    @PostMapping("/{id}/pin")
    String changePin(@PathVariable Long id, @RequestParam boolean pinned,
                     HttpServletRequest request, HttpServletResponse response,
                     RedirectAttributes redirectAttributes) {
        AdminIdentity admin = staffOrNull(request, response);
        if (admin == null) return redirect(request);
        lostItems.changePinnedAs(id, admin.userUuid(), pinned);
        redirectAttributes.addFlashAttribute("message", pinned ? "공지를 상단에 고정했습니다." : "상단 고정을 해제했습니다.");
        return "redirect:/admin/lost-items";
    }

    @PostMapping("/{id}/delete")
    String delete(@PathVariable Long id, HttpServletRequest request, HttpServletResponse response,
                  RedirectAttributes redirectAttributes) {
        AdminIdentity admin = staffOrNull(request, response);
        if (admin == null) return redirect(request);
        lostItems.deleteAs(id);
        redirectAttributes.addFlashAttribute("message", "분실물 공지를 삭제했습니다.");
        return "redirect:/admin/lost-items";
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
        model.addAttribute("statuses", LostItemStatus.values());
        model.addAttribute("sortOptions", LostItemSort.values());
        model.addAttribute("seoulZone", SEOUL);
    }
}
