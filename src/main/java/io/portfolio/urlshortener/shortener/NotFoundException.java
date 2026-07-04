package io.portfolio.urlshortener.shortener;

/** Short code does not exist (or has expired) — mapped to 404. */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String shortCode) {
        super("short code not found: " + shortCode);
    }
}
