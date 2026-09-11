package org.syu_likelion.Festa_2026.notice;

import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;
import org.syu_likelion.Festa_2026.error.ApiException;

/** Determines stored MIME types from a finite list; active web documents are not accepted. */
final class NoticeFilePolicy {
    private static final Map<String, String> TYPES = Map.ofEntries(
            Map.entry("jpg", "image/jpeg"), Map.entry("jpeg", "image/jpeg"),
            Map.entry("png", "image/png"), Map.entry("webp", "image/webp"), Map.entry("gif", "image/gif"),
            Map.entry("mp4", "video/mp4"), Map.entry("webm", "video/webm"), Map.entry("mov", "video/quicktime"),
            Map.entry("pdf", "application/pdf"), Map.entry("txt", "text/plain"),
            Map.entry("doc", "application/msword"), Map.entry("xls", "application/vnd.ms-excel"),
            Map.entry("ppt", "application/vnd.ms-powerpoint"),
            Map.entry("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
            Map.entry("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
            Map.entry("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation"),
            Map.entry("hwp", "application/x-hwp"), Map.entry("hwpx", "application/hwp+zip"),
            Map.entry("zip", "application/zip"));

    static Validated validate(MultipartFile file, long imageMax, long videoMax, long documentMax) {
        if (file == null || file.isEmpty()) throw invalid("EMPTY_ATTACHMENT_FILE", "빈 파일은 업로드할 수 없습니다.");
        String name = file.getOriginalFilename();
        name = name == null ? "" : name.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "").trim();
        if (name.isBlank() || name.length() > 255) throw invalid("INVALID_ATTACHMENT_FILENAME", "파일명을 확인해 주세요.");
        int dot = name.lastIndexOf('.');
        String extension = dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
        String type = TYPES.get(extension);
        if (type == null) throw invalid("UNSUPPORTED_ATTACHMENT_TYPE", "지원하지 않는 첨부파일 형식입니다.");
        long max = type.startsWith("image/") ? imageMax : type.startsWith("video/") ? videoMax : documentMax;
        if (file.getSize() > max) throw invalid("ATTACHMENT_FILE_TOO_LARGE", "첨부파일의 최대 크기를 초과했습니다.");
        return new Validated(name, extension, type, file.getSize(),
                !type.startsWith("image/") && !type.startsWith("video/"));
    }

    private static ApiException invalid(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message);
    }

    record Validated(String filename, String extension, String contentType, long size, boolean download) { }
}
