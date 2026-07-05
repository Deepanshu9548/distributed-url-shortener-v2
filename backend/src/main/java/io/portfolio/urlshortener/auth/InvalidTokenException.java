package io.portfolio.urlshortener.auth;

/** 401 — token signature/exp/typ/denylist check failed. */
public class InvalidTokenException extends RuntimeException {
    public InvalidTokenException() {
        super("invalid or expired token");
    }

    public InvalidTokenException(String message) {
        super(message);
    }
}
