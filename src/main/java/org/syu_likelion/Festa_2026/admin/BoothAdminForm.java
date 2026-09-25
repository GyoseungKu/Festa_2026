package org.syu_likelion.Festa_2026.admin;

import java.math.BigDecimal;
import org.syu_likelion.Festa_2026.booth.BoothCategory;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.multipart.MultipartFile;
import org.syu_likelion.Festa_2026.booth.BoothDtos.BoothAdminResponse;
import org.syu_likelion.Festa_2026.booth.BoothDtos.BoothMutationRequest;

public class BoothAdminForm {
    private BigDecimal latitude;
    private BigDecimal longitude;
    private String name;
    private String operator;
    private BoothCategory category = BoothCategory.GENERAL;
    private String description;
    @DateTimeFormat(pattern = "HH:mm") private LocalTime opensAt;
    @DateTimeFormat(pattern = "HH:mm") private LocalTime closesAt;
    private boolean stampEnabled;
    private List<UUID> managerUuids = new ArrayList<>();
    private List<MultipartFile> imageFiles = new ArrayList<>();
    private List<MultipartFile> videoFiles = new ArrayList<>();
    private List<Long> mediaOrderIds = new ArrayList<>();
    private Long representativeMediaId;
    private List<Long> removeMediaIds = new ArrayList<>();

    public static BoothAdminForm empty() {
        BoothAdminForm form = new BoothAdminForm();
        form.opensAt = LocalTime.of(10, 0); form.closesAt = LocalTime.of(18, 0);
        return form;
    }
    public static BoothAdminForm from(BoothAdminResponse response) {
        BoothAdminForm form = new BoothAdminForm();
        var booth = response.booth();
        form.latitude = booth.latitude(); form.longitude = booth.longitude(); form.name = booth.name();
        form.category = booth.category();
        form.operator = booth.operator(); form.description = booth.description();
        form.opensAt = booth.opensAt(); form.closesAt = booth.closesAt();
        form.stampEnabled = booth.stampEnabled();
        form.managerUuids = new ArrayList<>(response.managerUuids());
        form.mediaOrderIds = new ArrayList<>(booth.media().stream().map(item -> item.id()).toList());
        form.representativeMediaId = booth.representativeMedia() == null ? null : booth.representativeMedia().id();
        return form;
    }
    public BoothMutationRequest toRequest() {
        return new BoothMutationRequest(latitude, longitude, name, operator, description, opensAt, closesAt, stampEnabled,
                managerUuids == null ? List.of() : managerUuids, category);
    }
    public BigDecimal getLatitude() { return latitude; } public void setLatitude(BigDecimal v) { latitude = v; }
    public BigDecimal getLongitude() { return longitude; } public void setLongitude(BigDecimal v) { longitude = v; }
    public String getName() { return name; } public void setName(String v) { name = v; }
    public BoothCategory getCategory() { return category; }
    public void setCategory(BoothCategory category) { this.category = category; }
    public String getOperator() { return operator; } public void setOperator(String v) { operator = v; }
    public String getDescription() { return description; } public void setDescription(String v) { description = v; }
    public LocalTime getOpensAt() { return opensAt; } public void setOpensAt(LocalTime v) { opensAt = v; }
    public LocalTime getClosesAt() { return closesAt; } public void setClosesAt(LocalTime v) { closesAt = v; }
    public boolean isStampEnabled() { return stampEnabled; } public void setStampEnabled(boolean v) { stampEnabled = v; }
    public List<UUID> getManagerUuids() { return managerUuids; } public void setManagerUuids(List<UUID> v) { managerUuids = v; }
    public List<MultipartFile> getImageFiles() { return imageFiles; } public void setImageFiles(List<MultipartFile> v) { imageFiles = v; }
    public List<MultipartFile> getVideoFiles() { return videoFiles; } public void setVideoFiles(List<MultipartFile> v) { videoFiles = v; }
    public List<Long> getMediaOrderIds() { return mediaOrderIds; } public void setMediaOrderIds(List<Long> v) { mediaOrderIds = v; }
    public Long getRepresentativeMediaId() { return representativeMediaId; } public void setRepresentativeMediaId(Long v) { representativeMediaId = v; }
    public List<Long> getRemoveMediaIds() { return removeMediaIds; } public void setRemoveMediaIds(List<Long> v) { removeMediaIds = v; }
}
