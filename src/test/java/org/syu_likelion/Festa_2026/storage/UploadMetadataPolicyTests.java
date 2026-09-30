package org.syu_likelion.Festa_2026.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.syu_likelion.Festa_2026.error.ApiException;

class UploadMetadataPolicyTests {
    @Test
    void filenameAcceptsBoundaryAndRejectsOversizeInsteadOfTruncating() {
        assertThat(UploadMetadataPolicy.originalFilename("가".repeat(251) + ".jpg")).hasSize(255);
        assertThatThrownBy(() -> UploadMetadataPolicy.originalFilename("가".repeat(252) + ".jpg"))
                .isInstanceOfSatisfying(ApiException.class, error ->
                        assertThat(error.code()).isEqualTo("INVALID_MEDIA_FILENAME"));
        assertThat(UploadMetadataPolicy.originalFilename(null)).isEqualTo("upload");
    }

    @Test
    void locationChecksDatabaseCharacterLimitsAndObjectStorageByteLimit() {
        UploadMetadataPolicy.validateLocation("https://cdn.test", "a".repeat(512), 512);
        assertThatThrownBy(() -> UploadMetadataPolicy.validateLocation("https://cdn.test", "a".repeat(513), 512))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> UploadMetadataPolicy.validateLocation("https://cdn.test", "가".repeat(342), 1024))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> UploadMetadataPolicy.validateLocation("https://" + "a".repeat(2040), "file.jpg", 512))
                .isInstanceOf(ApiException.class);
    }
}
