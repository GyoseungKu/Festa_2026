package org.syu_likelion.Festa_2026.error;

import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.syu_likelion.Festa_2026.sso.SsoException;
import org.syu_likelion.Festa_2026.auth.TokenCookieManager;
import org.syu_likelion.Festa_2026.logging.ApiRequestContext;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
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

    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<ApiError> handleMethodValidation(HandlerMethodValidationException exception) {
        return ResponseEntity.badRequest().body(ApiError.of("INVALID_REQUEST", "요청 값을 확인해 주세요."));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ApiError> handleConstraintViolation(ConstraintViolationException exception) {
        String message = exception.getConstraintViolations().stream()
                .map(violation -> violation.getMessage()).distinct().collect(Collectors.joining(", "));
        return ResponseEntity.badRequest().body(ApiError.of("INVALID_REQUEST", message));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> handleUnreadableBody(HttpMessageNotReadableException exception) {
        return ResponseEntity.badRequest().body(ApiError.of("INVALID_REQUEST", "요청 JSON 형식을 확인해 주세요."));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ApiError> handleUploadTooLarge(MaxUploadSizeExceededException exception) {
        return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE)
                .body(ApiError.of("UPLOAD_TOO_LARGE", "업로드 가능한 파일 크기를 초과했습니다."));
    }

    @ExceptionHandler({MultipartException.class, MissingServletRequestPartException.class})
    ResponseEntity<ApiError> handleInvalidMultipart(Exception exception) {
        return ResponseEntity.badRequest()
                .body(ApiError.of("INVALID_MULTIPART_REQUEST", "파일 업로드 요청 형식을 확인해 주세요."));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ApiError> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException exception) {
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(ApiError.of("UNSUPPORTED_MEDIA_TYPE", "지원하지 않는 Content-Type입니다."));
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class})
    ResponseEntity<ApiError> handleInvalidParameter(Exception exception) {
        return ResponseEntity.badRequest()
                .body(ApiError.of("INVALID_PARAMETER", "요청 파라미터 형식을 확인해 주세요."));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ApiError> handleUnsupportedMethod(HttpRequestMethodNotSupportedException exception) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ApiError.of("METHOD_NOT_ALLOWED", "지원하지 않는 HTTP 메서드입니다."));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ApiError> handleMissingResource(NoResourceFoundException exception) {
        return ResponseEntity.status(404).body(ApiError.of("NOT_FOUND", "요청한 리소스를 찾을 수 없습니다."));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> handleUnexpected(Exception exception) {
        String requestId = ApiRequestContext.currentRequestId().orElse("unknown");
        log.error("Unhandled API exception requestId={} type={} success=false",
                requestId, exception.getClass().getName(), exception);
        return ResponseEntity.internalServerError().body(ApiError.of("INTERNAL_ERROR", "요청을 처리하지 못했습니다."));
    }
}
