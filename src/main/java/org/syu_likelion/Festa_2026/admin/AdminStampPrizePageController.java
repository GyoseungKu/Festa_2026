package org.syu_likelion.Festa_2026.admin;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.util.UUID;
import org.syu_likelion.Festa_2026.admin.AdminAccessService.AdminIdentity;
import org.syu_likelion.Festa_2026.error.ApiException;
import org.syu_likelion.Festa_2026.sso.SsoException;
import org.syu_likelion.Festa_2026.stamp.StampPrizeService;
import org.syu_likelion.Festa_2026.user.FestivalRole;

@Controller
@RequestMapping("/admin/stamps/prizes")
public class AdminStampPrizePageController {
    private final AdminAccessService access;
    private final AdminCookieManager cookies;
    private final StampPrizeService prizes;

    public AdminStampPrizePageController(AdminAccessService access, AdminCookieManager cookies, StampPrizeService prizes) {
        this.access = access; this.cookies = cookies; this.prizes = prizes;
    }

    @GetMapping
    String page(HttpServletRequest request, HttpServletResponse response, Model model) {
        AdminIdentity admin = authenticate(request, response);
        if (admin == null) return "redirect:/admin/login";
        populate(model, admin);
        return "admin/stamp-prizes";
    }

    @PostMapping("/qr/lookup")
    String lookup(@RequestParam String token, HttpServletRequest request, HttpServletResponse response, Model model) {
        return process(token, false, request, response, model);
    }

    @PostMapping("/qr/grant")
    String grant(@RequestParam String token, HttpServletRequest request, HttpServletResponse response, Model model) {
        return process(token, true, request, response, model);
    }

    @PostMapping("/search")
    String search(@RequestParam String query, @RequestParam(defaultValue = "0") int page,
                  HttpServletRequest request, HttpServletResponse response, Model model) {
        AdminIdentity admin = authenticate(request, response);
        if (admin == null) return "redirect:/admin/login";
        populate(model, admin);
        model.addAttribute("query", query);
        try { model.addAttribute("search", prizes.searchAs(role(admin), query, page)); }
        catch (ApiException e) { model.addAttribute("error", e.getMessage()); }
        catch (SsoException e) { model.addAttribute("error", "사용자 정보를 조회하지 못했습니다. 다시 시도해 주세요."); }
        return "admin/stamp-prizes";
    }

    @GetMapping("/users/{userUuid}")
    String user(@PathVariable UUID userUuid, HttpServletRequest request, HttpServletResponse response, Model model) {
        AdminIdentity admin = authenticate(request, response);
        if (admin == null) return "redirect:/admin/login";
        populate(model, admin);
        try { model.addAttribute("prizeTarget", prizes.lookupUserAs(role(admin), userUuid)); }
        catch (ApiException e) { model.addAttribute("error", e.getMessage()); }
        catch (SsoException e) { model.addAttribute("error", "사용자 정보를 조회하지 못했습니다. 다시 시도해 주세요."); }
        return "admin/stamp-prizes";
    }

    @PostMapping("/users/{userUuid}/grant")
    String grantUser(@PathVariable UUID userUuid, HttpServletRequest request, HttpServletResponse response, RedirectAttributes flash) {
        AdminIdentity admin = authenticate(request, response);
        if (admin == null) return "redirect:/admin/login";
        try {
            prizes.grantUserAs(admin.userUuid(), role(admin), userUuid);
            flash.addFlashAttribute("message", "상품 지급 완료로 기록했습니다. 확인한 사용자에게 상품을 전달해 주세요.");
        } catch (ApiException e) { flash.addFlashAttribute("error", e.getMessage()); }
        catch (SsoException e) { flash.addFlashAttribute("error", "사용자 정보를 확인하지 못해 지급하지 않았습니다. 다시 조회해 주세요."); }
        return "redirect:/admin/stamps/prizes/users/" + userUuid;
    }

    @GetMapping("/manage")
    String manage(@RequestParam(defaultValue = "0") int page, HttpServletRequest request, HttpServletResponse response, Model model) {
        AdminIdentity admin = authenticate(request, response);
        if (admin == null) return "redirect:/admin/login";
        populate(model, admin);
        model.addAttribute("records", prizes.listAs(role(admin), page));
        model.addAttribute("issuedCount", prizes.issuedCountAs(role(admin)));
        return "admin/stamp-prize-manage";
    }

    @GetMapping("/records/{id}")
    String record(@PathVariable Long id, @RequestParam(defaultValue = "0") int page,
                  HttpServletRequest request, HttpServletResponse response, Model model) {
        AdminIdentity admin = authenticate(request, response);
        if (admin == null) return "redirect:/admin/login";
        populate(model, admin);
        model.addAttribute("record", prizes.detailAs(role(admin), id));
        model.addAttribute("events", prizes.historyAs(role(admin), id, page));
        return "admin/stamp-prize-detail";
    }

    @PostMapping("/records/{id}/revoke")
    String revoke(@PathVariable Long id, @RequestParam long version, @RequestParam String reason,
                  HttpServletRequest request, HttpServletResponse response, RedirectAttributes flash) {
        AdminIdentity admin = authenticate(request, response);
        if (admin == null) return "redirect:/admin/login";
        try {
            prizes.revokeAs(admin.userUuid(), role(admin), id, version, reason);
            flash.addFlashAttribute("message", "상품 지급을 철회했습니다. 재지급하려면 사용자를 다시 조회해 주세요.");
        } catch (ApiException e) { flash.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/admin/stamps/prizes/records/" + id;
    }

    private FestivalRole role(AdminIdentity admin) {
        return admin.hasRole(FestivalRole.SUPER_ADMIN) ? FestivalRole.SUPER_ADMIN : FestivalRole.ADMIN;
    }

    private String process(String token, boolean grant, HttpServletRequest request,
                            HttpServletResponse response, Model model) {
        AdminIdentity admin = authenticate(request, response);
        if (admin == null) return "redirect:/admin/login";
        populate(model, admin);
        FestivalRole role = admin.hasRole(FestivalRole.SUPER_ADMIN) ? FestivalRole.SUPER_ADMIN : FestivalRole.ADMIN;
        try {
            model.addAttribute("prizeTarget", grant ? prizes.grantQrAs(admin.userUuid(), role, token)
                    : prizes.lookupQrAs(role, token));
            model.addAttribute("qrToken", token.trim());
            if (grant) model.addAttribute("message", "상품 지급 완료로 기록했습니다. 해당 사용자에게 상품을 전달해 주세요.");
        } catch (ApiException exception) {
            model.addAttribute("error", exception.getMessage());
        } catch (SsoException exception) {
            model.addAttribute("error", "SSO 사용자 정보를 조회하지 못했습니다. 새 QR로 다시 확인해 주세요.");
        }
        return "admin/stamp-prizes";
    }

    private AdminIdentity authenticate(HttpServletRequest request, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        AdminIdentity admin;
        try {
            var result = access.authenticate(cookies.readAccessToken(request), cookies.readRefreshToken(request));
            cookies.applyRotation(response, result.newAccessToken(), result.newRefreshToken());
            admin = result.body();
        } catch (ApiException | SsoException exception) {
            cookies.clear(response);
            return null;
        }
        if (!admin.hasRole(FestivalRole.ADMIN) && !admin.hasRole(FestivalRole.SUPER_ADMIN)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "STAMP_PRIZE_FORBIDDEN", "상품 지급은 ADMIN 이상만 처리할 수 있습니다.");
        }
        return admin;
    }

    private void populate(Model model, AdminIdentity admin) {
        model.addAttribute("adminName", admin.displayName());
        model.addAttribute("adminRole", admin.role());
        model.addAttribute("adminNavigationRoles", admin.roles());
        model.addAttribute("seoulZone", java.time.ZoneId.of("Asia/Seoul"));
    }
}
