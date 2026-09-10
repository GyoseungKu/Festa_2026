package org.syu_likelion.Festa_2026.admin;

import java.util.ArrayList;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;
import org.syu_likelion.Festa_2026.lostitem.LostItemDtos.LostItemMutationRequest;
import org.syu_likelion.Festa_2026.lostitem.LostItemDtos.LostItemResponse;
import org.syu_likelion.Festa_2026.lostitem.LostItemStatus;

public class LostItemAdminForm {
    private String title;
    private String content;
    private String foundLocation;
    public String getFoundLocation() { return foundLocation; }
    public void setFoundLocation(String foundLocation) { this.foundLocation = foundLocation; }
    private LostItemStatus status = LostItemStatus.HOLDING;
    private boolean pinned;
    private List<Long> removeImageIds = new ArrayList<>();
    private List<MultipartFile> imageFiles = new ArrayList<>();

    public static LostItemAdminForm empty() {
        return new LostItemAdminForm();
    }

    public static LostItemAdminForm from(LostItemResponse response) {
        LostItemAdminForm form = new LostItemAdminForm();
        form.title = response.title();
        form.content = response.content();
        form.foundLocation = response.foundLocation();
        form.status = response.status();
        form.pinned = response.pinned();
        return form;
    }

    public LostItemMutationRequest toRequest() {
        return new LostItemMutationRequest(title, content, status, pinned, foundLocation);
    }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public LostItemStatus getStatus() { return status; }
    public void setStatus(LostItemStatus status) { this.status = status; }
    public boolean isPinned() { return pinned; }
    public void setPinned(boolean pinned) { this.pinned = pinned; }
    public List<Long> getRemoveImageIds() { return removeImageIds; }
    public void setRemoveImageIds(List<Long> removeImageIds) {
        this.removeImageIds = removeImageIds == null ? new ArrayList<>() : removeImageIds;
    }
    public List<MultipartFile> getImageFiles() { return imageFiles; }
    public void setImageFiles(List<MultipartFile> imageFiles) {
        this.imageFiles = imageFiles == null ? new ArrayList<>() : imageFiles;
    }
}
