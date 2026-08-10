package org.syu_likelion.Festa_2026.sso;

public class SsoException extends RuntimeException {
    private final int statusCode;

    public SsoException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public SsoException(int statusCode, String message, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    public int statusCode() { return statusCode; }
}
