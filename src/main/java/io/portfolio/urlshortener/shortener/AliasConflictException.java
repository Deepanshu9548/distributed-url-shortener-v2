package io.portfolio.urlshortener.shortener;

/**
 * Requested custom alias is already taken — mapped to 409. No alternative
 * suggestions by design (ADR-011: predictable, no enumeration surface).
 */
public class AliasConflictException extends RuntimeException {

    public AliasConflictException(String alias) {
        super("custom alias already in use: " + alias);
    }
}
