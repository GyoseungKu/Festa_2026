package org.syu_likelion.Festa_2026.storage;

import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.syu_likelion.Festa_2026.error.ApiException;

/** Checks persisted upload metadata before writing an object to R2. */
public final class UploadMetadataPolicy {
    private UploadMetadataPolicy() { }

    public static String originalFilename(String value) {
        String name = StringUtils.cleanPath(value == null ? "upload" : value);
        if (name.isBlank() || name.length() > 255 || name.indexOf('\0') >= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_MEDIA_FILENAME",
                    "파일명은 255자 이내로 입력해 주세요.");
        }
        return name;
    }

    public static void validateLocation(String baseUrl, String storageKey, int keyColumnLength) {
        if (storageKey.length() > keyColumnLength
                || storageKey.getBytes(StandardCharsets.UTF_8).length > 1024
                || (baseUrl + "/" + storageKey).length() > 2048) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "INVALID_MEDIA_STORAGE_PATH",
                    "파일 저장 경로 설정을 확인해 주세요.");
        }
    }
}
