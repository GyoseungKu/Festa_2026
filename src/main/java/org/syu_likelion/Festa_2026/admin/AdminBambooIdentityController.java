package org.syu_likelion.Festa_2026.admin;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.ZoneId;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.syu_likelion.Festa_2026.admin.AdminAccessService.AdminIdentity;
import org.syu_likelion.Festa_2026.bamboo.BambooIdentityService;
import org.syu_likelion.Festa_2026.bamboo.BambooService;
import org.syu_likelion.Festa_2026.error.ApiException;
import org.syu_likelion.Festa_2026.user.FestivalRole;

@Controller
@RequestMapping("/admin/bamboo/users")
public class AdminBambooIdentityController {
    private final AdminAccessService access;
    private final AdminCookieManager cookies;
    private final BambooIdentityService identities;
    private final BambooService bamboo;

    public AdminBambooIdentityController(AdminAccessService access, AdminCookieManager cookies,
                                        BambooIdentityService identities, BambooService bamboo) {
        this.access = access;
        this.cookies = cookies;
        this.identities = identities;
        this.bamboo = bamboo;
    }

    @GetMapping
    String page(HttpServletRequest request, HttpServletResponse response, Model model) {
        common(authenticate(request, response), model);
        return "admin/bamboo/users";
    }

    @PostMapping("/search")
    String search(@RequestParam String query, @RequestParam(defaultValue = "0") int page,
                  HttpServletRequest request, HttpServletResponse response, Model model) {
        var admin = authenticate(request, response);
        common(admin, model);
        searchResults(admin, query, page, model);
        return "admin/bamboo/users";
    }

    @PostMapping("/{userUuid}/mute")
    String mute(@PathVariable UUID userUuid, @RequestParam int minutes, @RequestParam String reason,
                @RequestParam(defaultValue = "") String query, @RequestParam(defaultValue = "0") int page,
                HttpServletRequest request, HttpServletResponse response, Model model) {
        var admin = authenticate(request, response);
        common(admin, model);
        try {
            bamboo.muteUser(userUuid, minutes, admin.userUuid(), admin.displayName(), admin.role(), reason);
            model.addAttribute("message", minutes == 0 ? "선택한 사용자의 차단을 해제했습니다." : "선택한 사용자를 차단했습니다.");
        } catch (ApiException error) {
            model.addAttribute("error", error.getMessage());
        }
        if (!query.isBlank()) searchResults(admin, query, page, model);
        return "admin/bamboo/users";
    }

    private void searchResults(AdminIdentity admin, String query, int page, Model model) {
        model.addAttribute("query", query);
        try {
            var result = identities.search(admin.role(), admin.userUuid(), query, page);
            model.addAttribute("result", result);
            model.addAttribute("pageNumbers", AdminPagination.window(result.page(), result.totalPages()));
        } catch (ApiException error) {
            model.addAttribute("error", error.getMessage());
        }
    }

    private AdminIdentity authenticate(HttpServletRequest request, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        var result = access.authenticate(cookies.readAccessToken(request), cookies.readRefreshToken(request));
        cookies.applyRotation(response, result.newAccessToken(), result.newRefreshToken());
        if (result.body().role() != FestivalRole.SUPER_ADMIN) {
            throw new ApiException(HttpStatus.FORBIDDEN, "BAMBOO_MANAGE_FORBIDDEN",
                    "실제 신원으로 참여자를 조회·차단하는 기능은 SUPER_ADMIN만 사용할 수 있습니다.");
        }
        return result.body();
    }

    private void common(AdminIdentity admin, Model model) {
        model.addAttribute("adminName", admin.displayName());
        model.addAttribute("adminRole", admin.role());
        model.addAttribute("adminNavigationRoles", admin.roles());
        model.addAttribute("seoulZone", ZoneId.of("Asia/Seoul"));
        model.addAttribute("query", "");
    }
}
