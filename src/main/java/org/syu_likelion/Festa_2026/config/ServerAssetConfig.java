package org.syu_likelion.Festa_2026.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Keep server-rendered page assets separate from the React application's public paths. */
@Configuration
public class ServerAssetConfig implements WebMvcConfigurer {
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/admin/assets/**")
                .addResourceLocations("classpath:/static/");
        // Expose only the QR reader, not all WebJars (which would bypass Swagger access checks).
        registry.addResourceHandler("/admin/assets/webjars/jsqr/**")
                .addResourceLocations("classpath:/META-INF/resources/webjars/jsqr/");
    }
}
