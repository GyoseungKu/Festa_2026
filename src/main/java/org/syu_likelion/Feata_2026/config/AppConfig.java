package org.syu_likelion.Feata_2026.config;

import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.syu_likelion.Feata_2026.qr.QrProperties;
import org.syu_likelion.Feata_2026.admin.AdminProperties;

@Configuration
@EnableScheduling
@EnableConfigurationProperties({SsoProperties.class, AuthProperties.class, QrProperties.class,
        AdminProperties.class})
public class AppConfig {
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
