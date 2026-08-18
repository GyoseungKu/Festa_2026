package org.syu_likelion.Festa_2026.admin;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.ZoneId;
import java.util.Set;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.syu_likelion.Festa_2026.admin.AdminAccessService.AdminIdentity;
import org.syu_likelion.Festa_2026.auth.AuthorizedSsoExecutor.AuthorizedResult;
import org.syu_likelion.Festa_2026.birthday.BirthdayMessageAdminService;
import org.syu_likelion.Festa_2026.birthday.BirthdayMessageService;
import org.syu_likelion.Festa_2026.birthday.BirthdayMessageSort;
import org.syu_likelion.Festa_2026.user.FestivalRole;

@Controller
@RequestMapping("/admin/birthday-messages")
public class AdminBirthdayMessagePageController {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final int ADMIN_PAGE_SIZE = 20;
    private final AdminAccessService adminAccess;
    private final AdminCookieManager cookies;
    private final BirthdayMessageAdminService adminMessages;
    private final BirthdayMessageService messages;

    public AdminBirthdayMessagePageController(AdminAccessService adminAccess, AdminCookieManager cookies,
                                              BirthdayMessageAdminService adminMessages,
                                              BirthdayMessageService messages) {
        this.adminAccess = adminAccess;
        this.cookies = cookies;
        this.adminMessages = adminMessages;
        this.messages = messages;
    }

    @GetMapping
    String list(@RequestParam(defaultValue = "LATEST") BirthdayMessageSort sort,
                @RequestParam(defaultValue = "0") int page,
                HttpServletRequest request, HttpServletResponse response, Model model) {
        AdminIdentity admin = staffOrNull(request, response);
        if (admin == null) return redirect(request);
        common(model, admin);
        var result = adminMessages.listAs(Set.of(admin.role()), sort, page, ADMIN_PAGE_SIZE);
        model.addAttribute("result", result);
        model.addAttribute("pageNumbers", AdminPagination.window(result.page(), result.totalPages()));
        model.addAttribute("selectedSort", sort);
        model.addAttribute("sortOptions", BirthdayMessageSort.values());
        return "admin/birthday-messages/list";
    }

    @GetMapping("/{id}/hearts")
    String hearts(@PathVariable Long id, @RequestParam(defaultValue = "0") int page,
                  HttpServletRequest request, HttpServletResponse response, Model model) {
        AdminIdentity admin = staffOrNull(request, response);
        if (admin == null) return redirect(request);
        common(model, admin);
        var result = adminMessages.heartsAs(id, Set.of(admin.role()), page, ADMIN_PAGE_SIZE);
        model.addAttribute("result", result);
        model.addAttribute("pageNumbers", AdminPagination.window(result.page(), result.totalPages()));
        return "admin/birthday-messages/hearts";
    }

    @GetMapping("/{id}")
    String detail(@PathVariable Long id, HttpServletRequest request, HttpServletResponse response, Model model) {
        AdminIdentity admin = staffOrNull(request, response);
        if (admin == null) return redirect(request);
        common(model, admin);
        model.addAttribute("item", adminMessages.detailAs(id, Set.of(admin.role())));
        return "admin/birthday-messages/detail";
    }

    @PostMapping("/{id}/delete")
    String delete(@PathVariable Long id, HttpServletRequest request, HttpServletResponse response,
                  RedirectAttributes redirectAttributes) {
        AdminIdentity admin = staffOrNull(request, response);
        if (admin == null) return redirect(request);
        messages.deleteAsAdmin(id);
        redirectAttributes.addFlashAttribute("message", "생일축하 쪽지를 삭제했습니다. 작성자는 다시 작성할 수 있습니다.");
        return "redirect:/admin/birthday-messages";
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
        model.addAttribute("adminRole", admin.role());
        model.addAttribute("seoulZone", SEOUL);
    }
}
