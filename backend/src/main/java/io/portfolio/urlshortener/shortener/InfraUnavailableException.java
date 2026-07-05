package io.portfolio.urlshortener.shortener;

/**
 * Required infrastructure (database, id generation) is unavailable — mapped to
 * 503 in {@code GlobalExceptionHandler}. Deliberately distinct from
 * {@link NotFoundException} (404): "we don't know" must never masquerade as
 * "it doesn't exist" (frozen 503-vs-404 decision).
 */
public class InfraUnavailableException extends RuntimeException {

    public InfraUnavailableException(String message) {
        super(message);
    }

    public InfraUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
