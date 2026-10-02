package org.syu_likelion.Festa_2026.poll;

import org.springframework.web.multipart.MultipartFile;

public interface PollCoverImageStorage {
    StoredImage store(MultipartFile file);
    void delete(String storageKey);
    record StoredImage(String url, String storageKey, String originalFilename) { }
}
