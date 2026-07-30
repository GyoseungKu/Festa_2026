package org.syu_likelion.Feata_2026.error;

import java.util.stream.Collectors;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.syu_likelion.Feata_2026.sso.SsoException;
import org.syu_likelion.Feata_2026.auth.TokenCookieManager;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private final TokenCookieManager cookies;

    public GlobalExceptionHandler(TokenCookieManager cookies) {
        this.cookies = cookies;
    }

    @ExceptionHandler(SsoException.class)
    ResponseEntity<ApiError> handleSso(SsoException exception) {
        int upstream = exception.statusCode();
        if (upstream == 400) return ResponseEntity.badRequest().body(ApiError.of("SSO_INVALID_REQUEST", "요청 정보 또는 인증번호를 확인해 주세요."));
        if (upstream == 401) return ResponseEntity.status(401).header(TokenCookieManager.SET_COOKIE, cookies.clear())
                .body(ApiError.of("UNAUTHORIZED", "인증이 필요하거나 로그인 정보가 올바르지 않습니다."));
        if (upstream == 403) return ResponseEntity.status(403).header(TokenCookieManager.SET_COOKIE, cookies.clear())
                .body(ApiError.of("ACCOUNT_FORBIDDEN", "사용할 수 없는 계정입니다."));
        if (upstream == 409) return ResponseEntity.status(409).body(ApiError.of("ACCOUNT_CONFLICT", "이미 사용 중인 아이디 또는 이메일입니다."));
        if (upstream == 429) return ResponseEntity.status(429).body(ApiError.of("TOO_MANY_REQUESTS", "잠시 후 다시 시도해 주세요."));
        if (upstream == 503) return ResponseEntity.status(503).body(ApiError.of("SSO_UNAVAILABLE", "인증 서버에 일시적으로 연결할 수 없습니다."));
        return ResponseEntity.status(502).body(ApiError.of("SSO_BAD_GATEWAY", "인증 서버 응답을 처리할 수 없습니다."));
    }

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiError> handleApi(ApiException exception) {
        return ResponseEntity.status(exception.status()).body(ApiError.of(exception.code(), exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .distinct().collect(Collectors.joining(", "));
        return ResponseEntity.badRequest().body(ApiError.of("INVALID_REQUEST", message));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> handleUnexpected(Exception exception) {
        return ResponseEntity.internalServerError().body(ApiError.of("INTERNAL_ERROR", "요청을 처리하지 못했습니다."));
    }
}
