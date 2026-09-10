package org.syu_likelion.Festa_2026.sponsor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;
import org.syu_likelion.Festa_2026.booth.FestivalBoothRepository;
import org.syu_likelion.Festa_2026.error.ApiException;
import org.syu_likelion.Festa_2026.storage.TransactionalFileActions;

@Service
public class SponsorService {
    private final SponsorRepository sponsors;
    private final FestivalBoothRepository booths;
    private final SponsorImageStorage storage;
    public SponsorService(SponsorRepository sponsors, FestivalBoothRepository booths, SponsorImageStorage storage) {
        this.sponsors = sponsors; this.booths = booths; this.storage = storage;
    }
    public record SponsorResponse(Long id, String name, String description, String imageUrl,
                                  Long boothId, String boothName) { }

    @Transactional(readOnly = true)
    public List<SponsorResponse> list() {
        return sponsors.findAllByOrderByIdAsc().stream().map(this::response).toList();
    }
    @Transactional(readOnly = true)
    public SponsorResponse get(Long id) { return response(find(id)); }

    @Transactional
    public SponsorResponse save(Long id, UUID actor, String name, String description, Long boothId, MultipartFile image) {
        String cleanName = name == null ? "" : name.strip();
        String cleanDescription = description == null ? "" : description.strip();
        if (cleanName.isEmpty() || cleanName.length() > 100 ||
                cleanDescription.isEmpty() || cleanDescription.length() > 2000)
            throw new ApiException(HttpStatus.BAD_REQUEST, "SPONSOR_INVALID_INPUT",
                    "협찬사 이름은 1~100자, 설명은 1~2000자로 입력해 주세요.");
        Sponsor sponsor = id == null ? new Sponsor() : find(id);
        sponsor.booth = boothId == null ? null : booths.findById(boothId).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "BOOTH_NOT_FOUND", "선택한 부스가 없습니다."));
        boolean hasImage = image != null && !image.isEmpty();
        if (id == null && !hasImage)
            throw new ApiException(HttpStatus.BAD_REQUEST, "SPONSOR_IMAGE_REQUIRED", "협찬사 사진을 등록해 주세요.");
        if (hasImage) {
            var uploaded = storage.store(image);
            TransactionalFileActions.deleteOnRollback(() -> storage.delete(uploaded.storageKey()));
            String previousKey = sponsor.storageKey;
            sponsor.imageUrl = uploaded.url();
            sponsor.storageKey = uploaded.storageKey();
            if (previousKey != null)
                TransactionalFileActions.deleteAfterCommit(() -> storage.delete(previousKey));
        }
        Instant now = Instant.now();
        if (id == null) { sponsor.createdBy = actor; sponsor.createdAt = now; }
        sponsor.name = cleanName; sponsor.description = cleanDescription;
        sponsor.updatedBy = actor; sponsor.updatedAt = now;
        return response(sponsors.saveAndFlush(sponsor));
    }
    @Transactional
    public void delete(Long id) {
        Sponsor sponsor = find(id);
        String key = sponsor.storageKey;
        sponsors.delete(sponsor);
        sponsors.flush();
        TransactionalFileActions.deleteAfterCommit(() -> storage.delete(key));
    }
    private Sponsor find(Long id) {
        return sponsors.findById(id).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "SPONSOR_NOT_FOUND", "협찬사를 찾을 수 없습니다."));
    }
    private SponsorResponse response(Sponsor item) {
        return new SponsorResponse(item.id, item.name, item.description, item.imageUrl,
                item.booth == null ? null : item.booth.getId(),
                item.booth == null ? null : item.booth.getName());
    }
}

