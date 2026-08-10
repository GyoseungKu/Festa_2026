package org.syu_likelion.Festa_2026.sso;

public record SsoResult<T>(T body, String refreshToken) { }
