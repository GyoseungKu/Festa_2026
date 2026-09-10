package org.syu_likelion.Festa_2026.admin;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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
import org.syu_likelion.Festa_2026.booth.BoothDtos.BoothAdminResponse;
import org.syu_likelion.Festa_2026.booth.BoothDtos.BoothMediaOrderRequest;
import org.syu_likelion.Festa_2026.booth.BoothManagerDirectory;
import org.syu_likelion.Festa_2026.booth.BoothMediaKind;
import org.syu_likelion.Festa_2026.booth.BoothService;
import org.syu_likelion.Festa_2026.error.ApiException;
import org.syu_likelion.Festa_2026.user.FestivalRole;

@Controller
@RequestMapping("/admin/booths")
public class AdminBoothPageController {
    private final AdminAccessService adminAccess;
    private final AdminCookieManager cookies;
    private final BoothService booths;
    private final BoothManagerDirectory managers;
    public AdminBoothPageController(AdminAccessService adminAccess, AdminCookieManager cookies,
                                    BoothService booths, BoothManagerDirectory managers) {
        this.adminAccess = adminAccess; this.cookies = cookies; this.booths = booths; this.managers = managers;
    }

    @GetMapping String list(HttpServletRequest req, HttpServletResponse res, Model model) {
        AdminIdentity admin = managerOrNull(req, res); if (admin == null) return redirect(req);
        common(model, admin); model.addAttribute("booths", booths.listAdmin()); return "admin/booths/list";
    }
    @GetMapping("/new") String createPage(HttpServletRequest req, HttpServletResponse res, Model model) {
        AdminIdentity admin = managerOrNull(req, res); if (admin == null) return redirect(req);
        formModel(model, admin, BoothAdminForm.empty(), null, false); return "admin/booths/form";
    }
    @PostMapping String create(@ModelAttribute("form") BoothAdminForm form, HttpServletRequest req,
            HttpServletResponse res, Model model, RedirectAttributes flash) {
        AdminIdentity admin = managerOrNull(req, res); if (admin == null) return redirect(req);
        try {
            booths.createAs(admin.userUuid(), form.toRequest(), form.getImageFiles(), form.getVideoFiles());
            flash.addFlashAttribute("message", "부스를 등록했습니다."); return "redirect:/admin/booths";
        } catch (ApiException exception) {
            formModel(model, admin, form, null, false); model.addAttribute("error", exception.getMessage());
            return "admin/booths/form";
        }
    }
    @GetMapping("/{id}/edit") String editPage(@PathVariable Long id, HttpServletRequest req,
            HttpServletResponse res, Model model) {
        AdminIdentity admin = managerOrNull(req, res); if (admin == null) return redirect(req);
        BoothAdminResponse booth = booths.getAdmin(id); formModel(model, admin, BoothAdminForm.from(booth), booth, true);
        return "admin/booths/form";
    }
    @PostMapping("/{id}") String update(@PathVariable Long id, @ModelAttribute("form") BoothAdminForm form,
            HttpServletRequest req, HttpServletResponse res, Model model, RedirectAttributes flash) {
        AdminIdentity admin = managerOrNull(req, res); if (admin == null) return redirect(req);
        try {
            booths.updateAs(id, admin.userUuid(), form.toRequest());
            for (Long mediaId : form.getRemoveMediaIds() == null ? List.<Long>of() : form.getRemoveMediaIds())
                booths.deleteMediaAs(id, mediaId);
            booths.uploadAs(id, BoothMediaKind.IMAGE, form.getImageFiles());
            booths.uploadAs(id, BoothMediaKind.VIDEO, form.getVideoFiles());
            BoothAdminResponse current = booths.getAdmin(id);
            if (!current.booth().media().isEmpty()) {
                List<Long> currentIds = current.booth().media().stream().map(item -> item.id()).toList();
                List<Long> order = new ArrayList<>();
                if (form.getMediaOrderIds() != null) form.getMediaOrderIds().stream()
                        .filter(currentIds::contains).filter(idValue -> !order.contains(idValue)).forEach(order::add);
                currentIds.stream().filter(idValue -> !order.contains(idValue)).forEach(order::add);
                Long representative = form.getRepresentativeMediaId();
                if (representative == null || !currentIds.contains(representative))
                    representative = current.booth().representativeMedia().id();
                booths.orderMediaAs(id, new BoothMediaOrderRequest(order, representative));
            }
            flash.addFlashAttribute("message", "부스 정보를 수정했습니다."); return "redirect:/admin/booths";
        } catch (ApiException exception) {
            BoothAdminResponse booth = booths.getAdmin(id); formModel(model, admin, form, booth, true);
            model.addAttribute("error", exception.getMessage()); return "admin/booths/form";
        }
    }
    @PostMapping("/{id}/delete") String delete(@PathVariable Long id, HttpServletRequest req,
            HttpServletResponse res, RedirectAttributes flash) {
        AdminIdentity admin = managerOrNull(req, res); if (admin == null) return redirect(req);
        booths.deleteAs(id); flash.addFlashAttribute("message", "부스를 삭제했습니다."); return "redirect:/admin/booths";
    }
    private void formModel(Model model, AdminIdentity admin, BoothAdminForm form, BoothAdminResponse booth, boolean editing) {
        common(model, admin); model.addAttribute("form", form); model.addAttribute("booth", booth);
        model.addAttribute("editing", editing); model.addAttribute("managerCandidates", managers.candidates());
        model.addAttribute("selectedManagers", new HashSet<>(form.getManagerUuids() == null ? List.of() : form.getManagerUuids()));
    }
    private AdminIdentity managerOrNull(HttpServletRequest request, HttpServletResponse response) {
        try {
            AuthorizedResult<AdminIdentity> result = adminAccess.authenticate(cookies.readAccessToken(request), cookies.readRefreshToken(request));
            cookies.applyRotation(response, result.newAccessToken(), result.newRefreshToken());
            return result.body().role() == FestivalRole.ADMIN || result.body().role() == FestivalRole.SUPER_ADMIN ? result.body() : null;
        } catch (RuntimeException invalidLogin) { cookies.clear(response); return null; }
    }
    private String redirect(HttpServletRequest request) { return cookies.readAccessToken(request) == null ? "redirect:/admin/login" : "redirect:/admin"; }
    private void common(Model model, AdminIdentity admin) { model.addAttribute("adminName", admin.displayName()); model.addAttribute("adminRole", admin.role()); model.addAttribute("adminNavigationRoles", admin.roles()); }
}
