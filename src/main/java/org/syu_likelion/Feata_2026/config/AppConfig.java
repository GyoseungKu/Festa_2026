package org.syu_likelion.Feata_2026.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({SsoProperties.class, AuthProperties.class})
public class AppConfig { }
