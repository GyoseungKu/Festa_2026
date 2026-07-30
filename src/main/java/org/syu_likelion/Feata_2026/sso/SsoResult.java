package org.syu_likelion.Feata_2026.sso;

public record SsoResult<T>(T body, String refreshToken) { }
