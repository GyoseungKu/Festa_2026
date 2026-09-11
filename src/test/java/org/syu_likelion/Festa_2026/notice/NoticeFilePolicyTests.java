package org.syu_likelion.Festa_2026.notice;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import org.syu_likelion.Festa_2026.error.ApiException;

class NoticeFilePolicyTests {
    @Test
    void normalizesFilenameAndUsesKnownMimeTypeForDownloads() {
        var pdf = NoticeFilePolicy.validate(new MockMultipartFile("attachments", "C:\\fakepath\\안내.PDF",
                "text/html", new byte[]{1}), 10, 200, 20);
        assertThat(pdf.filename()).isEqualTo("안내.PDF");
        assertThat(pdf.contentType()).isEqualTo("application/pdf");
        assertThat(pdf.download()).isTrue();
        var movie = NoticeFilePolicy.validate(new MockMultipartFile("attachments", "clip.mp4",
                "application/octet-stream", new byte[]{1}), 10, 200, 20);
        assertThat(movie.contentType()).isEqualTo("video/mp4");
        assertThat(movie.download()).isFalse();
    }

    @Test
    void rejectsActiveDocumentsAndAppliesDifferentSizeLimits() {
        assertThatThrownBy(() -> NoticeFilePolicy.validate(new MockMultipartFile("attachments", "attack.svg",
                "image/svg+xml", new byte[]{1}), 10, 200, 20)).isInstanceOf(ApiException.class);
        for (String name : new String[]{"photo.png", "clip.mp4", "guide.pdf"}) {
            MultipartFile file = mock(MultipartFile.class);
            when(file.getOriginalFilename()).thenReturn(name);
            long limit = name.endsWith("png") ? 10 : name.endsWith("mp4") ? 200 : 20;
            when(file.getSize()).thenReturn(limit);
            assertThatCode(() -> NoticeFilePolicy.validate(file, 10, 200, 20)).doesNotThrowAnyException();
            when(file.getSize()).thenReturn(limit + 1);
            assertThatThrownBy(() -> NoticeFilePolicy.validate(file, 10, 200, 20))
                    .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("ATTACHMENT_FILE_TOO_LARGE"));
        }
    }
}
