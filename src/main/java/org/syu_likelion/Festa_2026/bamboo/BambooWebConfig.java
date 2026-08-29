package org.syu_likelion.Festa_2026.bamboo;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@EnableConfigurationProperties(BambooProperties.class)
public class BambooWebConfig implements WebMvcConfigurer {
    private final BambooConcurrencyInterceptor concurrency;

    public BambooWebConfig(BambooConcurrencyInterceptor concurrency) {
        this.concurrency = concurrency;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(concurrency)
                .addPathPatterns("/api/bamboo", "/api/bamboo/**")
                // SSE 는 연결이 오래 유지되므로 이 상한을 쓰면 곧바로 고갈된다.
                // 스트림은 사용자당 동시 연결 수로 따로 제한한다.
                .excludePathPatterns("/api/bamboo/stream");
    }
}
