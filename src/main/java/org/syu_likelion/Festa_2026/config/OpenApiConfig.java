package org.syu_likelion.Festa_2026.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    public static final String BEARER_AUTH = "bearerAuth";

    @Bean
    OpenAPI festivalOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("SYU Festa 2026 API")
                        .description("학교 축제 홈페이지 백엔드 API. 인증은 기존 SSO의 Access Token을 사용합니다.")
                        .version("v1"))
                .servers(List.of(
                        new Server().url("http://127.0.0.1:8888").description("로컬"),
                        new Server().url("https://festa.syu-likelion.org").description("배포")))
                .components(new Components().addSecuritySchemes(BEARER_AUTH,
                        new SecurityScheme()
                                .name(BEARER_AUTH)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
