package org.syu_likelion.Festa_2026.sponsor;

import org.springframework.web.multipart.MultipartFile;

public interface SponsorImageStorage {
    StoredImage store(MultipartFile file);
    void delete(String key);
    record StoredImage(String url, String storageKey) { }
}

