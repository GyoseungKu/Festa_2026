package org.syu_likelion.Festa_2026.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.syu_likelion.Festa_2026.auth.TokenCookieManager;

class GlobalExceptionHandlerTests {
    private final GlobalExceptionHandler handler =
            new GlobalExceptionHandler(mock(TokenCookieManager.class));

    @Test
    void mapsOversizedUploadToContentTooLarge() {
        ResponseEntity<ApiError> response =
                handler.handleUploadTooLarge(new MaxUploadSizeExceededException(1024));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONTENT_TOO_LARGE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("UPLOAD_TOO_LARGE");
    }

    @Test
    void mapsMalformedMultipartToBadRequest() {
        ResponseEntity<ApiError> response =
                handler.handleInvalidMultipart(new MultipartException("malformed"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("INVALID_MULTIPART_REQUEST");
    }
}
