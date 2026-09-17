package org.syu_likelion.Festa_2026.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import java.util.List;
import java.util.Set;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.RestController;
import org.syu_likelion.Festa_2026.error.ApiError;

@Configuration
public class OpenApiConfig {
    public static final String BEARER_AUTH = "bearerAuth";

    @Bean
    OpenAPI festivalOpenApi(AuthProperties auth) {
        Components components = new Components()
                .addSecuritySchemes(BEARER_AUTH, new SecurityScheme().name(BEARER_AUTH)
                        .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")
                        .description("POST /api/auth/login 응답의 accessToken을 입력합니다. Bearer 접두사는 UI가 추가합니다."))
                .addSecuritySchemes("refreshCookie", new SecurityScheme().type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.COOKIE).name(auth.refreshCookieName())
                        .description("API 로그인에서 발급하는 HttpOnly 쿠키. 브라우저가 저장·전송하며 직접 입력하지 않습니다. /admin 로그인 쿠키와 별개입니다."));
        ModelConverters.getInstance().read(ApiError.class).forEach(components::addSchemas);
        return new OpenAPI()
                .info(new Info()
                        .title("SYU Festa 2026 API")
                        .description("""
                                학교 축제 홈페이지 백엔드 API. SSO 사용자 UUID로 계정을 식별합니다.

                                ### Swagger에서 테스트하는 순서
                                1. `/admin/login?next=swagger` 로그인은 문서 열람용입니다. API Bearer 인증을 대신하지 않습니다.
                                2. Servers에서 현재 사용할 서버를 선택하고 `POST /api/auth/login`을 실행합니다.
                                3. 응답 `accessToken`을 **Authorize → bearerAuth**에 입력한 뒤 보호 API를 호출합니다.
                                4. Refresh 쿠키는 브라우저가 관리합니다. 로컬에서는 localhost와 127.0.0.1을 혼용하지 않습니다.

                                ### 프런트 공통 계약
                                - 인증 요청은 `credentials: include`, 보호 API는 `Authorization: Bearer <accessToken>`을 사용합니다.
                                - 갱신 응답의 `X-Access-Token`을 반영합니다. Swagger Authorize 값은 자동 교체되지 않습니다.
                                - 대나무숲·생일축하·투표/설문은 **조회부터 학생 인증 필수**입니다. 학생회비 납부는 이용 조건이 아닙니다.
                                - 오류는 `code` 기준으로 분기합니다. 최종 401은 재로그인, 학생 미인증 403은 학생 인증으로 안내합니다.
                                - 204 및 본문 없는 200은 JSON 파싱하지 않습니다. 학교 SSO 302는 브라우저 이동으로 처리합니다.
                                - PATCH도 필수 필드 전체 전송이 필요한 기능이 있습니다. 필드 생략/null 의미를 확인합니다.
                                - multipart는 FormData로 보내고 Content-Type을 직접 지정하지 않습니다.
                                - Instant는 UTC ISO-8601, LocalTime은 시간대 없는 시각입니다. 화면에서는 타입에 맞게 표시합니다.

                                `/admin/**` HTML 폼은 관리자 쿠키·CSRF를 사용하는 별도 인터페이스입니다.
                                전체 화면 구현 순서와 도메인별 예시는 저장소 `docs/frontend-getting-started.md` 및 `docs/README.md`를 참고합니다.
                                """)
                        .version("v1"))
                .servers(List.of(
                        new Server().url("http://127.0.0.1:8888").description("로컬"),
                        new Server().url("https://festa.syu-likelion.org").description("배포")))
                .components(components);
    }

    /** 문서 메타데이터만 보완한다. 실제 인증·응답 처리는 컨트롤러와 예외 처리기가 담당한다. */
    @Bean
    OperationCustomizer frontendErrorDocumentation() {
        return (operation, handler) -> {
            if (!handler.getBeanType().getPackageName().startsWith("org.syu_likelion.Festa_2026")
                    || !AnnotatedElementUtils.hasAnnotation(handler.getBeanType(), RestController.class)) {
                return operation;
            }
            operation.getResponses().putIfAbsent("default", jsonError(
                    "공통 API 오류. 400 입력 검증, 404 대상 없음, 409 상태 충돌, 429 요청 제한, 500 내부 오류, 502/503 외부 연동 오류 등. "
                    + "실제 발생 여부는 해당 기능에 따릅니다. code로 분기하며 모든 요청을 자동 재시도하지 않습니다. "
                    + "프록시·보안 필터에서 거부한 요청은 이 JSON 형식이 아닐 수 있습니다."));
            if (operation.getSecurity() != null && operation.getSecurity().stream()
                    .anyMatch(requirement -> requirement.containsKey(BEARER_AUTH))) {
                operation.getResponses().putIfAbsent("401", jsonError("UNAUTHORIZED: Bearer 누락 또는 인증 실패. 최종 실패 시 토큰·사용자 캐시를 정리합니다."));
                operation.getResponses().putIfAbsent("403", jsonError("역할·정책 제한. code를 확인하며 일반 권한 부족으로 로그아웃하지 않습니다."));
            }
            if (Set.of("BambooController", "BirthdayMessageController", "PollController")
                    .contains(handler.getBeanType().getSimpleName())) {
                operation.setDescription(operation.getDescription()
                        + "\n\n학생 인증 필수(조회 포함). 학교 미인증은 403 SCHOOL_VERIFICATION_REQUIRED이며 로그인 상태를 유지하고 학생 인증으로 안내합니다. 학생회비 납부 여부는 이용 조건이 아닙니다.");
                operation.getResponses().put("403", jsonError("SCHOOL_VERIFICATION_REQUIRED: 학생 인증 필요. 이 밖의 기능별 정책 거부도 code로 구분합니다."));
            }
            if (handler.getBeanType().getSimpleName().equals("AuthController")) {
                String method = handler.getMethod().getName();
                if (method.equals("login") || method.equals("signup")) {
                    operation.getResponses().putIfAbsent("409", jsonError(method.equals("login")
                            ? "ACCOUNT_REACTIVATION_REQUIRED: 명시적 복구 동의 필요. ACCOUNT_REACTIVATION_CONFLICT: 정보 충돌, 관리자 문의. 자동 복구 금지."
                            : "RECENTLY_WITHDRAWN_ACCOUNT: 탈퇴 30일 미만 정보 예약. 기존 계정 로그인·복구 또는 30일 경과 후 가입 안내. 중복 등 다른 충돌도 code로 구분합니다."));
                }
                if (method.equals("sendSignupEmail") || method.equals("sendRecoveryEmail")) {
                    operation.getResponses().putIfAbsent("429", jsonError("EMAIL_SEND_COOLDOWN: 동일 이메일 60초 재발송 제한. retryAfterSeconds만큼 대기합니다. 시간당 제한은 RATE_LIMIT_EXCEEDED. Retry-After는 제공될 때만 사용합니다."));
                }
            }
            return operation;
        };
    }

    private static ApiResponse jsonError(String description) {
        return new ApiResponse().description(description).content(new Content().addMediaType("application/json",
                new MediaType().schema(new Schema<>().$ref("#/components/schemas/ApiError"))));
    }
}
