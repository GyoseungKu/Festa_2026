package org.syu_likelion.Festa_2026.sso;

public final class SsoAccountStateException extends SsoException {
    private final String code;

    public SsoAccountStateException(int status, String code, String message) {
        super(status, message);
        this.code = code;
    }

    public String code() { return code; }
}
