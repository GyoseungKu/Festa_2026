package org.syu_likelion.Festa_2026.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "festival.withdrawal-email")
public record WithdrawalEmailProperties(boolean enabled, String from, String fromName, String siteUrl) { }
