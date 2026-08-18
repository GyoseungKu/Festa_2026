package org.syu_likelion.Festa_2026.admin;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.syu_likelion.Festa_2026.admin.AdminAccessService.AdminIdentity;
import org.syu_likelion.Festa_2026.auth.AuthorizedSsoExecutor.AuthorizedResult;
import org.syu_likelion.Festa_2026.error.ApiException;
import org.syu_likelion.Festa_2026.poll.PollDtos.PollAdminDetailResponse;
import org.syu_likelion.Festa_2026.poll.PollDtos.PollDetailResponse;
import org.syu_likelion.Festa_2026.poll.PollQuestionType;
import org.syu_likelion.Festa_2026.poll.PollService;
import org.syu_likelion.Festa_2026.user.FestivalRole;

@Controller
@RequestMapping("/admin/polls")
public class AdminPollPageController {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final int ADMIN_PAGE_SIZE = 20;
    private final AdminAccessService adminAccess;
    private final AdminCookieManager cookies;
    private final PollService polls;
    public AdminPollPageController(AdminAccessService adminAccess, AdminCookieManager cookies, PollService polls) {
        this.adminAccess = adminAccess; this.cookies = cookies; this.polls = polls;
    }

    @GetMapping String list(HttpServletRequest request, HttpServletResponse response, Model model) {
        AdminIdentity admin = managerOrNull(request, response); if (admin == null) return redirect(request);
        common(model, admin); model.addAttribute("polls", polls.listAdmin()); return "admin/polls/list";
    }
    @GetMapping("/new") String createPage(HttpServletRequest request, HttpServletResponse response, Model model) {
        AdminIdentity admin = managerOrNull(request, response); if (admin == null) return redirect(request);
        common(model, admin); model.addAttribute("form", PollAdminForm.empty()); model.addAttribute("editing", false);
        return "admin/polls/form";
    }
    @PostMapping String create(@ModelAttribute("form") PollAdminForm form, HttpServletRequest request,
            HttpServletResponse response, Model model, RedirectAttributes redirect) {
        AdminIdentity admin = managerOrNull(request, response); if (admin == null) return redirect(request);
        PollDetailResponse created = null;
        try {
            created = polls.createAs(admin.userUuid(), form.toMutation());
            applyUploads(created, form);
            redirect.addFlashAttribute("message", "투표를 생성했습니다.");
            return "redirect:/admin/polls/" + created.id();
        } catch (ApiException exception) {
            if (created != null) {
                try { polls.deleteAs(created.id(), admin.role(), false); }
                catch (RuntimeException cleanupFailure) { /* 생성 오류를 우선 표시하고 관리자 목록에서 정리할 수 있게 둡니다. */ }
            }
            common(model, admin); model.addAttribute("editing", false); model.addAttribute("error", exception.getMessage());
            return "admin/polls/form";
        }
    }
    @GetMapping("/{id}") String detail(@PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            HttpServletRequest request,
            HttpServletResponse response, Model model) {
        AdminIdentity admin = managerOrNull(request, response); if (admin == null) return redirect(request);
        PollAdminDetailResponse detail = polls.adminDetailAs(id, admin.role(), page, ADMIN_PAGE_SIZE);
        common(model, admin); model.addAttribute("detail", detail);
        model.addAttribute("pageNumbers", AdminPagination.window(detail.page(), detail.totalPages()));
        return "admin/polls/detail";
    }
    @GetMapping("/{id}/edit") String editPage(@PathVariable Long id, HttpServletRequest request,
            HttpServletResponse response, Model model) {
        AdminIdentity admin = managerOrNull(request, response); if (admin == null) return redirect(request);
        PollAdminDetailResponse detail = polls.adminDetailAs(id, admin.role());
        boolean locked = detail.submissionCount() > 0;
        common(model, admin); model.addAttribute("poll", detail.poll());
        model.addAttribute("form", PollAdminForm.from(detail.poll(), locked));
        model.addAttribute("editing", true); model.addAttribute("structureLocked", locked);
        return "admin/polls/form";
    }
    @PostMapping("/{id}") String update(@PathVariable Long id, @ModelAttribute("form") PollAdminForm form,
            HttpServletRequest request, HttpServletResponse response, Model model, RedirectAttributes redirect) {
        AdminIdentity admin = managerOrNull(request, response); if (admin == null) return redirect(request);
        try {
            if (!form.isSettingsOnly()) applyExistingQuestionMedia(id, form);
            PollDetailResponse updated = form.isSettingsOnly()
                    ? polls.updateSettingsAs(id, admin.userUuid(), form.toSettings())
                    : polls.updateDefinitionAs(id, admin.userUuid(), form.toMutation());
            if (!form.isSettingsOnly()) applyUploads(updated, form);
            redirect.addFlashAttribute("message", "투표 정보를 수정했습니다.");
            return "redirect:/admin/polls/" + id;
        } catch (ApiException exception) {
            common(model, admin); model.addAttribute("editing", true); model.addAttribute("poll", polls.adminDetailAs(id, admin.role()).poll());
            model.addAttribute("structureLocked", form.isSettingsOnly()); model.addAttribute("error", exception.getMessage());
            return "admin/polls/form";
        }
    }
    @PostMapping("/{id}/close") String close(@PathVariable Long id, HttpServletRequest request,
            HttpServletResponse response, RedirectAttributes redirect) {
        AdminIdentity admin = managerOrNull(request, response); if (admin == null) return redirect(request);
        polls.closeAs(id, admin.userUuid()); redirect.addFlashAttribute("message", "투표를 즉시 종료했습니다.");
        return "redirect:/admin/polls/" + id;
    }
    @PostMapping("/{id}/delete") String delete(@PathVariable Long id,
            @RequestParam(defaultValue = "false") boolean force, HttpServletRequest request,
            HttpServletResponse response, RedirectAttributes redirect) {
        AdminIdentity admin = managerOrNull(request, response); if (admin == null) return redirect(request);
        try { polls.deleteAs(id, admin.role(), force); redirect.addFlashAttribute("message", "투표를 삭제했습니다.");
            return "redirect:/admin/polls";
        } catch (ApiException exception) { redirect.addFlashAttribute("error", exception.getMessage()); return "redirect:/admin/polls/" + id; }
    }

    private void applyUploads(PollDetailResponse poll, PollAdminForm form) {
        int questionCount = Math.min(poll.questions().size(), form.getQuestions().size());
        for (int qi = 0; qi < questionCount; qi++) {
            var formQuestion = form.getQuestions().get(qi);
            List<org.springframework.web.multipart.MultipartFile> mediaFiles = formQuestion.getMediaFiles() == null
                    ? List.of() : formQuestion.getMediaFiles().stream().filter(file -> file != null && !file.isEmpty()).toList();
            if (!mediaFiles.isEmpty()) polls.uploadQuestionMediaAs(poll.id(), poll.questions().get(qi).id(), mediaFiles);
            var responseOptions = poll.questions().get(qi).options();
            var formOptions = formQuestion.getOptions();
            for (int oi = 0; oi < Math.min(responseOptions.size(), formOptions.size()); oi++) {
                var option = formOptions.get(oi); Long optionId = responseOptions.get(oi).id();
                if (option.getImage() != null && !option.getImage().isEmpty())
                    polls.replaceOptionImageAs(poll.id(), optionId, option.getImage());
                else if (option.isRemoveImage()) polls.removeOptionImageAs(poll.id(), optionId);
            }
        }
    }
    private void applyExistingQuestionMedia(Long pollId, PollAdminForm form) {
        if (form.getQuestions() == null) return;
        for (var question : form.getQuestions()) {
            if (question.getId() == null || question.getMedia() == null) continue;
            for (var media : question.getMedia())
                if (media.isRemove() && media.getId() != null)
                    polls.removeQuestionMediaAs(pollId, question.getId(), media.getId());
            List<Long> order = question.getMedia().stream()
                    .filter(media -> !media.isRemove() && media.getId() != null)
                    .sorted(Comparator.comparingInt(PollAdminForm.QuestionMediaForm::getDisplayOrder))
                    .map(PollAdminForm.QuestionMediaForm::getId).toList();
            if (!order.isEmpty()) polls.reorderQuestionMediaAs(pollId, question.getId(), order);
        }
    }
    private AdminIdentity managerOrNull(HttpServletRequest request, HttpServletResponse response) {
        try {
            AuthorizedResult<AdminIdentity> result = adminAccess.authenticate(cookies.readAccessToken(request), cookies.readRefreshToken(request));
            cookies.applyRotation(response, result.newAccessToken(), result.newRefreshToken());
            return result.body().role() == FestivalRole.ADMIN || result.body().role() == FestivalRole.SUPER_ADMIN ? result.body() : null;
        } catch (RuntimeException invalid) { cookies.clear(response); return null; }
    }
    private String redirect(HttpServletRequest request) { return cookies.readAccessToken(request) == null ? "redirect:/admin/login" : "redirect:/admin"; }
    private void common(Model model, AdminIdentity admin) {
        model.addAttribute("adminName", admin.displayName()); model.addAttribute("adminRole", admin.role());
        model.addAttribute("questionTypes", PollQuestionType.values()); model.addAttribute("seoulZone", SEOUL);
        model.addAttribute("superAdmin", admin.role() == FestivalRole.SUPER_ADMIN);
    }
}
