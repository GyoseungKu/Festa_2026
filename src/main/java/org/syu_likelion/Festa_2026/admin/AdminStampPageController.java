package org.syu_likelion.Festa_2026.admin;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.syu_likelion.Festa_2026.admin.AdminAccessService.AdminIdentity;
import org.syu_likelion.Festa_2026.auth.AuthorizedSsoExecutor.AuthorizedResult;
import org.syu_likelion.Festa_2026.booth.BoothManagerDirectory;
import org.syu_likelion.Festa_2026.booth.FestivalBooth;
import org.syu_likelion.Festa_2026.error.ApiException;
import org.syu_likelion.Festa_2026.sso.SsoException;
import org.syu_likelion.Festa_2026.stamp.StampDtos.BoothStampAdminResponse;
import org.syu_likelion.Festa_2026.stamp.StampDtos.StampTargetResponse;
import org.syu_likelion.Festa_2026.stamp.StampService;
import org.syu_likelion.Festa_2026.user.FestivalRole;

@Controller
@RequestMapping("/admin/stamps")
public class AdminStampPageController {
    private final AdminAccessService adminAccess;
    private final AdminCookieManager cookies;
    private final StampService stamps;
    private final BoothManagerDirectory users;

    public AdminStampPageController(AdminAccessService adminAccess, AdminCookieManager cookies,
                                    StampService stamps, BoothManagerDirectory users) {
        this.adminAccess = adminAccess; this.cookies = cookies; this.stamps = stamps; this.users = users;
    }

    @GetMapping
    String page(@RequestParam(required = false) Long boothId,
                @RequestParam(defaultValue = "0") int page,
                @RequestParam(defaultValue = "30") int size,
                HttpServletRequest request,
                HttpServletResponse response, Model model) {
        AdminIdentity admin = stampAdminOrNull(request, response);
        if (admin == null) return redirect(request);
        populate(model, admin, boothId, page, size);
        return "admin/stamps";
    }

    @PostMapping("/qr/lookup")
    String lookup(@RequestParam Long boothId, @RequestParam String token, HttpServletRequest request,
                  HttpServletResponse response, Model model) {
        AdminIdentity admin = stampAdminOrNull(request, response);
        if (admin == null) return redirect(request);
        populate(model, admin, boothId, 0, 30);
        try {
            model.addAttribute("stampTarget", stamps.lookupQrAs(admin.userUuid(), stampRole(admin), boothId, token));
            model.addAttribute("qrToken", token == null ? null : token.trim());
        } catch (ApiException exception) {
            model.addAttribute("error", exception.getMessage());
        } catch (SsoException exception) {
            model.addAttribute("error", "SSO 사용자 정보를 조회하지 못했습니다. 잠시 후 다시 시도해 주세요.");
        }
        return "admin/stamps";
    }

    @PostMapping("/qr/action")
    String qrAction(@RequestParam Long boothId, @RequestParam String token, @RequestParam String action,
                    HttpServletRequest request, HttpServletResponse response, Model model) {
        AdminIdentity admin = stampAdminOrNull(request, response);
        if (admin == null) return redirect(request);
        populate(model, admin, boothId, 0, 30);
        try {
            StampTargetResponse target = "REVOKE".equals(action)
                    ? stamps.revokeQrAs(admin.userUuid(), stampRole(admin), boothId, token)
                    : stamps.grantQrAs(admin.userUuid(), stampRole(admin), boothId, token);
            model.addAttribute("stampTarget", target);
            model.addAttribute("qrToken", token.trim());
            model.addAttribute("message", "REVOKE".equals(action) ? "스탬프를 회수했습니다." : "스탬프를 지급했습니다.");
            refreshHistory(model, admin, boothId, 0, 30);
        } catch (ApiException exception) {
            model.addAttribute("error", exception.getMessage());
        }
        return "admin/stamps";
    }

    @PostMapping("/user/action")
    String userAction(@RequestParam Long boothId, @RequestParam UUID userUuid, @RequestParam String action,
                      HttpServletRequest request, HttpServletResponse response, RedirectAttributes redirect) {
        AdminIdentity admin = stampAdminOrNull(request, response);
        if (admin == null) return redirect(request);
        if (!isAdmin(admin)) return "redirect:/admin";
        try {
            if ("REVOKE".equals(action)) stamps.revokeBySearchAs(admin.userUuid(), boothId, userUuid);
            else stamps.grantBySearchAs(admin.userUuid(), boothId, userUuid);
            redirect.addFlashAttribute("message", "REVOKE".equals(action) ? "스탬프를 회수했습니다." : "스탬프를 지급했습니다.");
        } catch (ApiException exception) {
            redirect.addFlashAttribute("error", exception.getMessage());
        } catch (SsoException exception) {
            redirect.addFlashAttribute("error", "SSO 사용자 정보를 조회하지 못했습니다. 잠시 후 다시 시도해 주세요.");
        }
        return "redirect:/admin/stamps?boothId=" + boothId;
    }

    private void populate(Model model, AdminIdentity admin, Long requestedBoothId, int page, int size) {
        FestivalRole effectiveRole = stampRole(admin);
        List<FestivalBooth> available = stamps.availableBoothsAs(admin.userUuid(), effectiveRole);
        Long selected = requestedBoothId;
        if (selected == null && !available.isEmpty()) selected = available.getFirst().getId();
        Long selectedId = selected;
        if (selectedId != null && available.stream().noneMatch(booth -> booth.getId().equals(selectedId)))
            throw new ApiException(org.springframework.http.HttpStatus.FORBIDDEN, "STAMP_MANAGE_FORBIDDEN", "스탬프 관리 권한이 없습니다.");
        model.addAttribute("adminName", admin.displayName());
        model.addAttribute("adminRole", admin.role());
        model.addAttribute("seoulZone", java.time.ZoneId.of("Asia/Seoul"));
        model.addAttribute("booths", available);
        model.addAttribute("selectedBoothId", selectedId);
        model.addAttribute("canAdminOverride", isAdmin(admin));
        if (isAdmin(admin)) {
            model.addAttribute("userCandidates", users.candidates());
        }
        refreshHistory(model, admin, selectedId, page, size);
    }

    private void refreshHistory(Model model, AdminIdentity admin, Long boothId, int page, int size) {
        if (boothId == null) return;
        BoothStampAdminResponse history = stamps.historyAs(admin.userUuid(), stampRole(admin), boothId, page, size);
        model.addAttribute("stampAdmin", history);
        if (isAdmin(admin)) {
            Set<UUID> stamped = new HashSet<>(history.currentStamps().stream().map(item -> item.userUuid()).toList());
            model.addAttribute("stampedUserIds", stamped);
        }
    }

    private AdminIdentity stampAdminOrNull(HttpServletRequest request, HttpServletResponse response) {
        try {
            AuthorizedResult<AdminIdentity> result = adminAccess.authenticate(
                    cookies.readAccessToken(request), cookies.readRefreshToken(request));
            cookies.applyRotation(response, result.newAccessToken(), result.newRefreshToken());
            AdminIdentity identity = result.body();
            return identity.hasRole(FestivalRole.BOOTH_MANAGER) || isAdmin(identity) ? identity : null;
        } catch (RuntimeException invalidLogin) {
            cookies.clear(response); return null;
        }
    }
    private boolean isAdmin(AdminIdentity admin) {
        return admin.hasRole(FestivalRole.ADMIN) || admin.hasRole(FestivalRole.SUPER_ADMIN);
    }
    private FestivalRole stampRole(AdminIdentity admin) {
        if (admin.hasRole(FestivalRole.SUPER_ADMIN)) return FestivalRole.SUPER_ADMIN;
        if (admin.hasRole(FestivalRole.ADMIN)) return FestivalRole.ADMIN;
        if (admin.hasRole(FestivalRole.BOOTH_MANAGER)) return FestivalRole.BOOTH_MANAGER;
        throw new ApiException(org.springframework.http.HttpStatus.FORBIDDEN,
                "STAMP_MANAGE_FORBIDDEN", "스탬프 관리 권한이 없습니다.");
    }
    private String redirect(HttpServletRequest request) {
        return cookies.readAccessToken(request) == null ? "redirect:/admin/login" : "redirect:/admin";
    }
}
