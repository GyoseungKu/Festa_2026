package org.syu_likelion.Festa_2026.lostitem;

import org.springframework.web.multipart.MultipartFile;

public interface LostItemImageStorage {
    StoredImage store(MultipartFile file);
    void delete(String storageKey);

    record StoredImage(String url, String storageKey, String originalFilename) { }
}
