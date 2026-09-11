package org.syu_likelion.Festa_2026.notice;

import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;
import org.syu_likelion.Festa_2026.error.ApiException;

/** Validates the two upload groups before any file is stored. */
public final class NoticeUploads {
    private NoticeUploads() { }

    public static List<MultipartFile> combine(List<MultipartFile> media, List<MultipartFile> files) {
        List<MultipartFile> result = new ArrayList<>();
        add(result, media, false);
        add(result, files, true);
        return result;
    }

    private static void add(List<MultipartFile> result, List<MultipartFile> uploads, boolean download) {
        if (uploads == null) return;
        for (MultipartFile file : uploads) {
            if (file == null || file.isEmpty()) continue;
            var data = NoticeFilePolicy.validate(file, Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE);
            if (data.download() != download) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "NOTICE_ATTACHMENT_GROUP_MISMATCH",
                        download ? "파일 첨부에는 문서·압축파일을 선택해 주세요." : "미디어에는 이미지·영상을 선택해 주세요.");
            }
            result.add(file);
        }
    }
}
