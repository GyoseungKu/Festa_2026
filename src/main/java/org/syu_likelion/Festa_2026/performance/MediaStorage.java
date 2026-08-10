package org.syu_likelion.Festa_2026.performance;

import org.springframework.web.multipart.MultipartFile;

public interface MediaStorage {
    StoredFile store(MultipartFile file, PerformanceMediaKind kind);
    void delete(String storageKey);

    record StoredFile(String url, String storageKey, String originalFilename) { }
}
