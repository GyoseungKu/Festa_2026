package org.syu_likelion.Festa_2026.config;

import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.syu_likelion.Festa_2026.qr.QrProperties;
import org.syu_likelion.Festa_2026.admin.AdminProperties;
import org.syu_likelion.Festa_2026.performance.R2Properties;
import org.syu_likelion.Festa_2026.logging.ApiRequestLogProperties;
import org.syu_likelion.Festa_2026.analytics.FrontendAnalyticsProperties;
import org.syu_likelion.Festa_2026.monitoring.MonitoringProperties;
import org.syu_likelion.Festa_2026.auth.WelcomeEmailProperties;
import org.syu_likelion.Festa_2026.schoolsso.SchoolSsoProperties;

@Configuration
@EnableScheduling
@EnableConfigurationProperties({SsoProperties.class, AuthProperties.class, QrProperties.class,
        AdminProperties.class, R2Properties.class, ApiRequestLogProperties.class,
        FrontendAnalyticsProperties.class, MonitoringProperties.class, WelcomeEmailProperties.class,
        SchoolSsoProperties.class})
public class AppConfig {
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
