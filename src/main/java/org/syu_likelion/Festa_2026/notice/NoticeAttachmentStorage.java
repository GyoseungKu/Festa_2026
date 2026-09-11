package org.syu_likelion.Festa_2026.notice;

import org.springframework.web.multipart.MultipartFile;

public interface NoticeAttachmentStorage {
    StoredAttachment store(MultipartFile file);
    void delete(String storageKey);

    record StoredAttachment(String url, String storageKey, String originalFilename, String contentType, long size) { }
}
