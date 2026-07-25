package io.portfolio.urlshortener.shortener;

/** Client input failed validation — mapped to 400. */
public class ValidationException extends RuntimeException {

    public ValidationException(String message) {
        super(message);
    }
}
