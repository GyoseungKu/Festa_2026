package org.syu_likelion.Festa_2026.admin;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.syu_likelion.Festa_2026.booth.FestivalBoothRepository;
import org.syu_likelion.Festa_2026.error.ApiException;
import org.syu_likelion.Festa_2026.sponsor.SponsorService;
import org.syu_likelion.Festa_2026.user.FestivalRole;

@Controller
@RequestMapping("/admin/sponsors")
public class AdminSponsorPageController {
    private final AdminAccessService access;
    private final AdminCookieManager cookies;
    private final SponsorService sponsors;
    private final FestivalBoothRepository booths;
    public AdminSponsorPageController(AdminAccessService access, AdminCookieManager cookies,
                                      SponsorService sponsors, FestivalBoothRepository booths) {
        this.access = access; this.cookies = cookies; this.sponsors = sponsors; this.booths = booths;
    }
    @GetMapping
    String list(HttpServletRequest req, HttpServletResponse res, Model model) {
        if (!authorize(req, res, model)) return "redirect:/admin/login";
        model.addAttribute("sponsors", sponsors.list());
        return "admin/sponsors/list";
    }
    @GetMapping("/new")
    String createPage(HttpServletRequest req, HttpServletResponse res, Model model) {
        if (!authorize(req, res, model)) return "redirect:/admin/login";
        form(model, null, "", "", null);
        return "admin/sponsors/form";
    }
    @GetMapping("/{id}/edit")
    String edit(@PathVariable Long id, HttpServletRequest req, HttpServletResponse res, Model model) {
        if (!authorize(req, res, model)) return "redirect:/admin/login";
        var item = sponsors.get(id);
        form(model, id, item.name(), item.description(), item.boothId());
        return "admin/sponsors/form";
    }
    @PostMapping
    String create(@RequestParam String name, @RequestParam String description,
            @RequestParam(required = false) Long boothId, @RequestParam(required = false) MultipartFile image,
            HttpServletRequest req, HttpServletResponse res, Model model, RedirectAttributes flash) {
        return save(null, name, description, boothId, image, req, res, model, flash);
    }
    @PostMapping("/{id}")
    String update(@PathVariable Long id, @RequestParam String name, @RequestParam String description,
            @RequestParam(required = false) Long boothId, @RequestParam(required = false) MultipartFile image,
            HttpServletRequest req, HttpServletResponse res, Model model, RedirectAttributes flash) {
        return save(id, name, description, boothId, image, req, res, model, flash);
    }
    private String save(Long id, String name, String description, Long boothId, MultipartFile image,
            HttpServletRequest req, HttpServletResponse res, Model model, RedirectAttributes flash) {
        if (!authorize(req, res, model)) return "redirect:/admin/login";
        var actor = (AdminAccessService.AdminIdentity) model.getAttribute("actor");
        try {
            sponsors.save(id, actor.userUuid(), name, description, boothId, image);
            flash.addFlashAttribute("message", "협찬사 정보를 저장했습니다.");
            return "redirect:/admin/sponsors";
        } catch (ApiException failure) {
            form(model, id, name, description, boothId);
            model.addAttribute("error", failure.getMessage());
            return "admin/sponsors/form";
        }
    }
    @PostMapping("/{id}/delete")
    String delete(@PathVariable Long id, HttpServletRequest req, HttpServletResponse res, Model model,
                  RedirectAttributes flash) {
        if (!authorize(req, res, model)) return "redirect:/admin/login";
        sponsors.delete(id);
        flash.addFlashAttribute("message", "협찬사를 삭제했습니다.");
        return "redirect:/admin/sponsors";
    }
    private boolean authorize(HttpServletRequest req, HttpServletResponse res, Model model) {
        AdminAccessService.AdminIdentity actor;
        try {
            var auth = access.authenticate(cookies.readAccessToken(req), cookies.readRefreshToken(req));
            cookies.applyRotation(res, auth.newAccessToken(), auth.newRefreshToken());
            actor = auth.body();
        } catch (ApiException unauthenticated) {
            return false;
        }
        if (actor.role() != FestivalRole.ADMIN && actor.role() != FestivalRole.SUPER_ADMIN)
            throw new ApiException(org.springframework.http.HttpStatus.FORBIDDEN,
                    "SPONSOR_MANAGE_FORBIDDEN", "협찬사 관리는 ADMIN 이상만 가능합니다.");
        model.addAttribute("actor", actor);
        model.addAttribute("adminName", actor.displayName());
        model.addAttribute("adminRole", actor.role());
        return true;
    }
    private void form(Model model, Long id, String name, String description, Long boothId) {
        model.addAttribute("sponsorId", id);
        model.addAttribute("name", name);
        model.addAttribute("description", description);
        model.addAttribute("boothId", boothId);
        model.addAttribute("booths", booths.findAllByOrderByNameAsc());
        model.addAttribute("imageUrl", id == null ? null : sponsors.get(id).imageUrl());
    }
}

