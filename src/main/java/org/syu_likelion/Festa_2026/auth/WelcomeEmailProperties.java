package org.syu_likelion.Festa_2026.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "festival.welcome-email")
public record WelcomeEmailProperties(
        boolean enabled,
        String from,
        String fromName,
        String subject,
        String siteUrl) {
}
