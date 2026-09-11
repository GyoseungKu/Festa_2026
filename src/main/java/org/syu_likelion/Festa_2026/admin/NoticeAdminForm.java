package org.syu_likelion.Festa_2026.admin;

import java.util.ArrayList;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;
import org.syu_likelion.Festa_2026.notice.NoticeDtos.NoticeMutationRequest;
import org.syu_likelion.Festa_2026.notice.NoticeDtos.NoticeResponse;

public class NoticeAdminForm {
    private String title;
    private String content;
    private boolean pinned;
    private List<Long> removeAttachmentIds = new ArrayList<>();
    private List<MultipartFile> attachmentFiles = new ArrayList<>();

    public static NoticeAdminForm empty() {
        return new NoticeAdminForm();
    }

    public static NoticeAdminForm from(NoticeResponse response) {
        NoticeAdminForm form = new NoticeAdminForm();
        form.title = response.title();
        form.content = response.content();
        form.pinned = response.pinned();
        return form;
    }

    public NoticeMutationRequest toRequest() {
        return new NoticeMutationRequest(title, content, pinned);
    }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public boolean isPinned() { return pinned; }
    public void setPinned(boolean pinned) { this.pinned = pinned; }
    public List<Long> getRemoveAttachmentIds() { return removeAttachmentIds; }
    public void setRemoveAttachmentIds(List<Long> removeAttachmentIds) {
        this.removeAttachmentIds = removeAttachmentIds == null ? new ArrayList<>() : removeAttachmentIds;
    }
    public List<MultipartFile> getAttachmentFiles() { return attachmentFiles; }
    public void setAttachmentFiles(List<MultipartFile> attachmentFiles) {
        this.attachmentFiles = attachmentFiles == null ? new ArrayList<>() : attachmentFiles;
    }
}
