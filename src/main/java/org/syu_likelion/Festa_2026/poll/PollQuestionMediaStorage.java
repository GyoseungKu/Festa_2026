package org.syu_likelion.Festa_2026.poll;

import org.springframework.web.multipart.MultipartFile;

public interface PollQuestionMediaStorage {
    StoredMedia store(MultipartFile file);
    void delete(String storageKey);
    record StoredMedia(PollQuestionMediaKind kind, String url, String storageKey, String originalFilename) { }
}
