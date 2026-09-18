package org.syu_likelion.Festa_2026.admin;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.syu_likelion.Festa_2026.qr.UserDirectoryService;
import org.syu_likelion.Festa_2026.sso.SsoException;
import org.syu_likelion.Festa_2026.user.FestivalRole;

@Controller
public class AdminUserDirectoryController {
    private final AdminAccessService access;
    private final AdminCookieManager cookies;
    private final UserDirectoryService directory;

    public AdminUserDirectoryController(AdminAccessService access, AdminCookieManager cookies, UserDirectoryService directory) {
        this.access = access;
        this.cookies = cookies;
        this.directory = directory;
    }

    @GetMapping("/admin/qr/users")
    String list(@RequestParam(required = false) FestivalRole role,
                @RequestParam(required = false) Boolean verified,
                @RequestParam(required = false) Boolean paid,
                @RequestParam(defaultValue = "0") int page,
                @RequestParam(defaultValue = "100") int size,
                HttpServletRequest request, HttpServletResponse response, Model model) {
        response.setHeader("Cache-Control", "no-store");
        if (cookies.readAccessToken(request) == null) return "redirect:/admin/login";
        var authenticated = access.authenticate(cookies.readAccessToken(request), cookies.readRefreshToken(request));
        cookies.applyRotation(response, authenticated.newAccessToken(), authenticated.newRefreshToken());
        var admin = authenticated.body();
        model.addAttribute("adminName", admin.displayName());
        model.addAttribute("adminRole", admin.role());
        model.addAttribute("adminNavigationRoles", admin.roles());
        model.addAttribute("role", role);
        model.addAttribute("verified", verified);
        model.addAttribute("paid", paid);
        model.addAttribute("size", Math.max(1, Math.min(size, 100)));
        model.addAttribute("roleOptions", FestivalRole.values());
        try {
            var result = directory.list(admin.role(), role, verified, paid, page, size);
            model.addAttribute("result", result);
            model.addAttribute("pages", AdminPagination.window(result.getNumber(), result.getTotalPages()));
        } catch (SsoException failure) {
            model.addAttribute("error", "SSO 사용자 정보를 조회하지 못했습니다. 잠시 후 다시 조회해 주세요.");
        }
        return "admin/user-directory";
    }
}
