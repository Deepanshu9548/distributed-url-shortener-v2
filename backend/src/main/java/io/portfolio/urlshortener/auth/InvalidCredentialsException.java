package io.portfolio.urlshortener.auth;

/**
 * 401 — bad email or bad password. Deliberately does not distinguish so the
 * error message can't be used to enumerate registered accounts.
 */
public class InvalidCredentialsException extends RuntimeException {
    public InvalidCredentialsException() {
        super("invalid credentials");
    }
}
