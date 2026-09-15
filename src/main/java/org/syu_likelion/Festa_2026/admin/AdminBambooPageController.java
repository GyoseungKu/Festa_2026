package org.syu_likelion.Festa_2026.admin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.syu_likelion.Festa_2026.admin.AdminAccessService.AdminIdentity;
import org.syu_likelion.Festa_2026.auth.AuthorizedSsoExecutor.AuthorizedResult;
import org.syu_likelion.Festa_2026.bamboo.BambooAdminService;
import org.syu_likelion.Festa_2026.bamboo.BambooMessageStatus;
import org.syu_likelion.Festa_2026.bamboo.BambooService;
import org.syu_likelion.Festa_2026.error.ApiException;
import org.syu_likelion.Festa_2026.user.FestivalRole;

/**
 * 대나무숲 운영 화면.
 *
 * <p>축제 당일에는 담당자가 PC 로 교대 근무하며 신고를 처리한다. 화면은 신고 목록과
 * 최근 메시지, 그리고 킬스위치를 한 페이지에 둔다.
 */
@Controller
@RequestMapping("/admin/bamboo")
public class AdminBambooPageController {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter LOCAL_INPUT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
    private static final int PAGE_SIZE = 30;
    private static final int PARTICIPANT_PAGE_SIZE = 20;
    private static final int AUDIT_PAGE_SIZE = 20;

    private final AdminAccessService adminAccess;
    private final AdminCookieManager cookies;
    private final BambooService bamboo;
    private final BambooAdminService bambooAdmin;

    public AdminBambooPageController(AdminAccessService adminAccess, AdminCookieManager cookies,
                                     BambooService bamboo, BambooAdminService bambooAdmin) {
        this.adminAccess = adminAccess;
        this.cookies = cookies;
        this.bamboo = bamboo;
        this.bambooAdmin = bambooAdmin;
    }

    @GetMapping
    String list(@RequestParam(defaultValue = "LIVE") String tab,
                @RequestParam(defaultValue = "0") int page,
                @RequestParam(defaultValue = "") String participantQuery,
                @RequestParam(defaultValue = "0") int participantPage,
                @RequestParam(defaultValue = "0") int auditPage,
                HttpServletRequest request, HttpServletResponse response, Model model) {
        AdminIdentity admin = staffOrNull(request, response);
        if (admin == null) return redirect(request);
        common(model, admin);
        boolean live = "LIVE".equalsIgnoreCase(tab) || "RECENT".equalsIgnoreCase(tab);
        var result = live ? bamboo.recentMessages(page, PAGE_SIZE) : bamboo.reportedMessages(page, PAGE_SIZE);
        model.addAttribute("tab", live ? "LIVE" : "REPORTED");
        model.addAttribute("result", result);
        model.addAttribute("messages", result.items());
        model.addAttribute("pageNumbers", AdminPagination.window(result.page(), result.totalPages()));
        model.addAttribute("liveCursor", bamboo.currentCursor());
        if (isAdmin(admin.role())) {
            var participants = bamboo.participants(participantQuery, participantPage, PARTICIPANT_PAGE_SIZE);
            model.addAttribute("participantQuery", participantQuery == null ? "" : participantQuery.strip());
            model.addAttribute("participants", participants);
            model.addAttribute("participantPageNumbers",
                    AdminPagination.window(participants.page(), participants.totalPages()));
            var audits = bamboo.moderationHistory(auditPage, AUDIT_PAGE_SIZE);
            model.addAttribute("audits", audits);
            model.addAttribute("auditPageNumbers", AdminPagination.window(audits.page(), audits.totalPages()));
        }
        model.addAttribute("settings", bamboo.settingsView());
        return "admin/bamboo/list";
    }

    /** 본문 전체를 다시 받기 전에 실제 메시지 변경이 있는지만 확인하는 폴링 응답이다. */
    @GetMapping("/cursor")
    @ResponseBody
    @Tag(name = "Admin Console", description = "관리자 웹 로그인 쿠키로 인증하는 운영 화면용 JSON API")
    @Operation(summary = "대나무숲 관리자 변경 커서 조회", description = "관리자 웹 로그인 쿠키와 STAFF 이상 권한이 필요합니다. 메시지 변경 여부를 확인할 수 있는 현재 cursor를 반환합니다. 인증 또는 권한 확인에 실패하면 401을 반환합니다.")
    ResponseEntity<Map<String, Long>> cursor(HttpServletRequest request, HttpServletResponse response) {
        AdminIdentity admin = staffOrNull(request, response);
        if (admin == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(Map.of("cursor", bamboo.currentCursor()));
    }

    @PostMapping("/participants/mute")
    String muteParticipant(@RequestParam String nickname, @RequestParam int minutes,
                           @RequestParam String reason,
                           @RequestParam(defaultValue = "LIVE") String tab,
                           @RequestParam(defaultValue = "") String participantQuery,
                           @RequestParam(defaultValue = "0") int participantPage,
                           HttpServletRequest request, HttpServletResponse response,
                           RedirectAttributes flash) {
        AdminIdentity admin = staffOrNull(request, response);
        if (admin == null) return redirect(request);
        if (!isAdmin(admin.role())) {
            flash.addFlashAttribute("error", "작성 차단은 ADMIN 이상만 처리할 수 있습니다.");
            return back(tab);
        }
        try {
            bamboo.muteParticipant(nickname, minutes, admin.userUuid(), admin.displayName(),
                    admin.role(), reason);
            flash.addFlashAttribute("message", minutes <= 0
                    ? nickname + " 참여자의 작성 차단을 해제했습니다."
                    : nickname + " 참여자를 " + minutes + "분간 차단했습니다.");
        } catch (ApiException exception) {
            flash.addFlashAttribute("error", exception.getMessage());
        }
        String query = org.springframework.web.util.UriUtils.encodeQueryParam(
                participantQuery == null ? "" : participantQuery, java.nio.charset.StandardCharsets.UTF_8);
        return "redirect:/admin/bamboo?tab=" + normalizedTab(tab)
                + "&participantQuery=" + query + "&participantPage=" + Math.max(0, participantPage);
    }

    @PostMapping("/messages/{id}/status")
    String changeStatus(@PathVariable Long id, @RequestParam BambooMessageStatus status,
                        @RequestParam(defaultValue = "REPORTED") String tab,
                        HttpServletRequest request, HttpServletResponse response,
                        RedirectAttributes flash) {
        AdminIdentity admin = staffOrNull(request, response);
        if (admin == null) return redirect(request);
        bamboo.changeStatus(List.of(id), status, admin.userUuid());
        flash.addFlashAttribute("message", switch (status) {
            case VISIBLE -> "메시지를 다시 표시했습니다.";
            case HIDDEN -> "메시지를 가렸습니다.";
            case BLOCKED -> "메시지를 차단했습니다.";
            case DELETED -> "메시지를 삭제했습니다. 원문은 기록으로 남습니다.";
        });
        return back(tab);
    }

    @PostMapping("/messages/{id}/mute")
    String mute(@PathVariable Long id, @RequestParam int minutes, @RequestParam String reason,
                @RequestParam(defaultValue = "REPORTED") String tab,
                HttpServletRequest request, HttpServletResponse response, RedirectAttributes flash) {
        AdminIdentity admin = staffOrNull(request, response);
        if (admin == null) return redirect(request);
        try {
            bamboo.muteAuthorOf(id, minutes, admin.userUuid(), admin.displayName(), admin.role(), reason);
            flash.addFlashAttribute("message", minutes <= 0
                    ? "작성 차단을 해제했습니다." : "작성자를 " + minutes + "분간 차단했습니다.");
        } catch (ApiException exception) {
            flash.addFlashAttribute("error", exception.getMessage());
        }
        return back(tab);
    }

    @PostMapping("/messages/{id}/nickname")
    String rename(@PathVariable Long id, @RequestParam String nickname,
                  @RequestParam(defaultValue = "REPORTED") String tab,
                  HttpServletRequest request, HttpServletResponse response, RedirectAttributes flash) {
        AdminIdentity admin = staffOrNull(request, response);
        if (admin == null) return redirect(request);
        try {
            var renamed = bamboo.renameAuthorOf(id, nickname);
            flash.addFlashAttribute("message", "닉네임을 " + renamed.nickname() + " 으로 변경했습니다.");
        } catch (ApiException exception) {
            flash.addFlashAttribute("error", exception.getMessage());
        }
        return back(tab);
    }

    /**
     * 작성자 신원 조회. 조회 자체가 감사 대상이므로 GET 이 아니라 POST 로 두어
     * 새로고침만으로 기록이 반복해서 쌓이지 않게 한다.
     */
    @PostMapping("/messages/{id}/author")
    String author(@PathVariable Long id, @RequestParam(defaultValue = "REPORTED") String tab,
                  HttpServletRequest request, HttpServletResponse response, RedirectAttributes flash) {
        AdminIdentity admin = staffOrNull(request, response);
        if (admin == null) return redirect(request);
        try {
            flash.addFlashAttribute("author", bambooAdmin.authorAs(admin.role(), admin.userUuid(), id));
            flash.addFlashAttribute("authorMessageId", id);
        } catch (ApiException exception) {
            flash.addFlashAttribute("error", exception.getMessage());
        } catch (RuntimeException failure) {
            flash.addFlashAttribute("error", "SSO 사용자 정보를 조회하지 못했습니다. 잠시 후 다시 시도해 주세요.");
        }
        return back(tab);
    }

    @PostMapping("/settings")
    String settings(@RequestParam(required = false) Boolean enabled,
                    @RequestParam(required = false) Boolean readOnly,
                    @RequestParam(required = false) String closesAt,
                    HttpServletRequest request, HttpServletResponse response, RedirectAttributes flash) {
        AdminIdentity admin = staffOrNull(request, response);
        if (admin == null) return redirect(request);
        if (admin.role() != FestivalRole.ADMIN && admin.role() != FestivalRole.SUPER_ADMIN) {
            flash.addFlashAttribute("error", "운영 설정은 ADMIN 이상만 변경할 수 있습니다.");
            return back("REPORTED");
        }
        boolean clear = closesAt == null || closesAt.isBlank();
        try {
            bamboo.updateSettings(Boolean.TRUE.equals(enabled), Boolean.TRUE.equals(readOnly),
                    clear ? null : LocalDateTime.parse(closesAt.trim(), LOCAL_INPUT)
                            .atZone(SEOUL).toInstant(), clear);
            flash.addFlashAttribute("message", "운영 설정을 저장했습니다.");
        } catch (java.time.format.DateTimeParseException invalid) {
            flash.addFlashAttribute("error", "종료 시각 형식을 확인해 주세요.");
        }
        return back("REPORTED");
    }

    private String back(String tab) {
        return "redirect:/admin/bamboo?tab=" + normalizedTab(tab);
    }

    private String normalizedTab(String tab) {
        return "LIVE".equalsIgnoreCase(tab) || "RECENT".equalsIgnoreCase(tab) ? "LIVE" : "REPORTED";
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
        model.addAttribute("canChangeSettings", admin.role() == FestivalRole.ADMIN
                || admin.role() == FestivalRole.SUPER_ADMIN);
        model.addAttribute("canSeeAuthor", admin.role() == FestivalRole.SUPER_ADMIN);
        model.addAttribute("canManageBlocks", isAdmin(admin.role()));
        model.addAttribute("seoulZone", SEOUL);
        model.addAttribute("closesAtInput", closesAtInput());
    }

    private boolean isAdmin(FestivalRole role) {
        return role == FestivalRole.ADMIN || role == FestivalRole.SUPER_ADMIN;
    }

    private String closesAtInput() {
        Instant closesAt = bamboo.settingsView().closesAt();
        return closesAt == null ? "" : LocalDateTime.ofInstant(closesAt, SEOUL).format(LOCAL_INPUT);
    }
}
