package org.syu_likelion.Festa_2026.booth;

import org.springframework.web.multipart.MultipartFile;

public interface BoothMediaStorage {
    StoredFile store(MultipartFile file, BoothMediaKind kind);
    void delete(String storageKey);
    record StoredFile(String url, String storageKey, String originalFilename) { }
}
