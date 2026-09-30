package org.syu_likelion.Festa_2026.admin;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.ui.Model;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.syu_likelion.Festa_2026.fee.StudentFeeService;
import org.syu_likelion.Festa_2026.error.ApiException;

@Controller
@RequestMapping("/admin/student-fees")
public class AdminStudentFeeController {
    private final AdminAccessService access;
    private final AdminCookieManager cookies;
    private final StudentFeeService fees;
    public AdminStudentFeeController(AdminAccessService access, AdminCookieManager cookies, StudentFeeService fees) {
        this.access = access; this.cookies = cookies; this.fees = fees;
    }
    private AdminAccessService.AdminIdentity authenticate(HttpServletRequest request, HttpServletResponse response) {
        var auth = access.authenticate(cookies.readAccessToken(request), cookies.readRefreshToken(request));
        cookies.applyRotation(response, auth.newAccessToken(), auth.newRefreshToken());
        StudentFeeService.requireSuperAdmin(auth.body().role());
        response.setHeader("Cache-Control", "no-store");
        return auth.body();
    }
    @GetMapping
    String list(@RequestParam(defaultValue = "") String query, @RequestParam(defaultValue = "0") int page,
                HttpServletRequest request, HttpServletResponse response, Model model) {
        var actor = authenticate(request, response);
        model.addAttribute("adminName", actor.displayName());
        model.addAttribute("adminRole", actor.role()); model.addAttribute("adminNavigationRoles", actor.roles());
        model.addAttribute("query", query);
        model.addAttribute("payers", fees.list(actor.role(), query, page));
        return "admin/student-fees";
    }
    @PostMapping
    String add(@RequestParam String studentNumbers, HttpServletRequest request, HttpServletResponse response,
               RedirectAttributes flash) {
        var actor = authenticate(request, response);
        try {
            var result = fees.add(actor.role(), actor.userUuid(), studentNumbers);
            flash.addFlashAttribute("message", result.added() + "건 추가, " + result.duplicates() + "건 중복 제외. 사용자 납부 여부 재검증 완료.");
        } catch (ApiException invalid) { flash.addFlashAttribute("error", invalid.getMessage()); }
        return "redirect:/admin/student-fees";
    }
    @PostMapping("/delete")
    String delete(@RequestParam String studentNo, HttpServletRequest request, HttpServletResponse response,
                  RedirectAttributes flash) {
        var actor = authenticate(request, response);
        fees.delete(actor.role(), studentNo);
        flash.addFlashAttribute("message", "학번을 삭제하고 사용자 납부 여부를 재검증했습니다.");
        return "redirect:/admin/student-fees";
    }
}
