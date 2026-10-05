package org.syu_likelion.Festa_2026.admin;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.syu_likelion.Festa_2026.error.ApiException;
import org.syu_likelion.Festa_2026.sso.SsoException;
import org.syu_likelion.Festa_2026.wristband.*;

@Controller
@RequestMapping("/admin/wristbands")
public class AdminWristbandController {
    private final AdminAccessService access;
    private final AdminCookieManager cookies;
    private final WristbandService wristbands;
    private final WristbandLookupService lookup;
    private final ManualWristbandService manual;
    public AdminWristbandController(AdminAccessService access, AdminCookieManager cookies,
            WristbandService wristbands, WristbandLookupService lookup, ManualWristbandService manual) {
        this.access = access; this.cookies = cookies; this.wristbands = wristbands; this.lookup = lookup;
        this.manual = manual;
    }
    private AdminAccessService.AdminIdentity authenticate(HttpServletRequest request, HttpServletResponse response, Model model) {
        var auth = access.authenticate(cookies.readAccessToken(request), cookies.readRefreshToken(request));
        cookies.applyRotation(response, auth.newAccessToken(), auth.newRefreshToken());
        var actor = auth.body();
        WristbandService.requireStaff(actor.role());
        response.setHeader("Cache-Control", "no-store");
        if (model != null) {
            model.addAttribute("adminName", actor.displayName());
            model.addAttribute("adminRole", actor.role());
            model.addAttribute("adminNavigationRoles", actor.roles());
            model.addAttribute("canManage", WristbandService.isAdmin(actor.role()));
            model.addAttribute("seoulZone", java.time.ZoneId.of("Asia/Seoul"));
        }
        return actor;
    }
    @GetMapping
    String page(HttpServletRequest request, HttpServletResponse response, Model model) {
        authenticate(request, response, model);
        return "admin/wristbands/issue";
    }
    @PostMapping("/scan")
    String scan(@RequestParam String token, HttpServletRequest request, HttpServletResponse response, Model model) {
        var actor = authenticate(request, response, model);
        try { model.addAttribute("candidates", List.of(lookup.scan(actor.role(), token))); }
        catch (ApiException e) { model.addAttribute("error", e.getMessage()); }
        catch (SsoException e) { model.addAttribute("error", "사용자 정보를 조회하지 못했습니다. 다시 시도해 주세요."); }
        return "admin/wristbands/issue";
    }
    @PostMapping("/search")
    String search(@RequestParam String query, @RequestParam(defaultValue = "0") int page,
            HttpServletRequest request, HttpServletResponse response, Model model) {
        var actor = authenticate(request, response, model);
        model.addAttribute("query", query);
        try {
            var result = lookup.search(actor.role(), query, page);
            model.addAttribute("search", result); model.addAttribute("candidates", result.items());
        } catch (ApiException e) { model.addAttribute("error", e.getMessage()); }
        catch (SsoException e) { model.addAttribute("error", "사용자 정보를 조회하지 못했습니다. 다시 시도해 주세요."); }
        return "admin/wristbands/issue";
    }
    @PostMapping("/issue")
    String issue(@RequestParam UUID userUuid, HttpServletRequest request, HttpServletResponse response, RedirectAttributes flash) {
        var actor = authenticate(request, response, null);
        try {
            wristbands.issue(actor.role(), actor.userUuid(), actor.displayName(), userUuid);
            flash.addFlashAttribute("message", "팔찌 지급을 기록했습니다. 조회한 사용자에게 팔찌를 전달해 주세요.");
        } catch (ApiException e) { flash.addFlashAttribute("error", e.getMessage()); }
        catch (SsoException e) { flash.addFlashAttribute("error", "사용자 정보를 확인하지 못해 지급하지 않았습니다. 다시 조회해 주세요."); }
        return "redirect:/admin/wristbands";
    }
    @PostMapping("/manual/search")
    String manualSearch(@RequestParam String studentNo, HttpServletRequest request,
            HttpServletResponse response, Model model) {
        var actor = authenticate(request, response, model);
        WristbandService.requireAdmin(actor.role());
        model.addAttribute("manualStudentNo", studentNo);
        try { model.addAttribute("manualResult", manual.lookup(actor.role(), studentNo)); }
        catch (ApiException e) { model.addAttribute("error", e.getMessage()); }
        return "admin/wristbands/issue";
    }
    @PostMapping("/manual/issue")
    String manualIssue(@RequestParam String studentNo, @RequestParam(required = false) String name,
            @RequestParam(required = false) String department, HttpServletRequest request,
            HttpServletResponse response, RedirectAttributes flash) {
        var actor = authenticate(request, response, null);
        WristbandService.requireAdmin(actor.role());
        try {
            var record = manual.issue(actor.role(), actor.userUuid(), actor.displayName(), studentNo, name, department);
            flash.addFlashAttribute("message", "팔찌 지급을 기록했습니다. 본인을 확인한 학생에게 팔찌를 전달해 주세요.");
            return "redirect:/admin/wristbands/records/" + record.getId();
        } catch (ApiException e) { flash.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/admin/wristbands";
    }
    @PostMapping("/records/{id}/profile")
    String manualProfile(@PathVariable long id, @RequestParam long version,
            @RequestParam(required = false) String name, @RequestParam(required = false) String department,
            HttpServletRequest request, HttpServletResponse response, RedirectAttributes flash) {
        var actor = authenticate(request, response, null);
        WristbandService.requireAdmin(actor.role());
        try {
            manual.updateProfile(actor.role(), actor.userUuid(), actor.displayName(), id, version, name, department);
            flash.addFlashAttribute("message", "수령자 정보를 저장했습니다.");
        } catch (ApiException e) { flash.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/admin/wristbands/records/" + id;
    }
    @GetMapping("/manage")
    String manage(@RequestParam(defaultValue = "0") int page, HttpServletRequest request, HttpServletResponse response, Model model) {
        var actor = authenticate(request, response, model);
        model.addAttribute("records", wristbands.list(actor.role(), page));
        model.addAttribute("issuedCount", wristbands.issuedCount(actor.role()));
        return "admin/wristbands/manage";
    }
    @GetMapping("/records/{id}")
    String detail(@PathVariable long id, @RequestParam(defaultValue = "0") int page,
            HttpServletRequest request, HttpServletResponse response, Model model) {
        var actor = authenticate(request, response, model);
        model.addAttribute("wristband", wristbands.detail(actor.role(), id));
        model.addAttribute("events", wristbands.history(actor.role(), id, page));
        return "admin/wristbands/detail";
    }
    @PostMapping("/records/{id}/revoke")
    String revoke(@PathVariable long id, @RequestParam long version, @RequestParam String reason,
            HttpServletRequest request, HttpServletResponse response, RedirectAttributes flash) {
        var actor = authenticate(request, response, null);
        WristbandService.requireAdmin(actor.role());
        try {
            wristbands.revoke(actor.role(), actor.userUuid(), actor.displayName(), id, version, reason);
            flash.addFlashAttribute("message", "팔찌 지급을 철회했습니다. 재지급 시 사용자를 다시 조회해 주세요.");
        } catch (ApiException e) { flash.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/admin/wristbands/records/" + id;
    }
}
