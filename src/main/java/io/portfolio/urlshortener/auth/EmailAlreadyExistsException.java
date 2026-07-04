package io.portfolio.urlshortener.auth;

/** 409 — {@link User#emailNormalized} already exists. */
public class EmailAlreadyExistsException extends RuntimeException {
    public EmailAlreadyExistsException() {
        super("email already registered");
    }
}
