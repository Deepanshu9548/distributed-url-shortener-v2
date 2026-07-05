package io.portfolio.urlshortener.common;

import io.portfolio.urlshortener.auth.EmailAlreadyExistsException;
import io.portfolio.urlshortener.auth.InvalidCredentialsException;
import io.portfolio.urlshortener.auth.InvalidTokenException;
import io.portfolio.urlshortener.shortener.AliasConflictException;
import io.portfolio.urlshortener.shortener.InfraUnavailableException;
import io.portfolio.urlshortener.shortener.NotFoundException;
import io.portfolio.urlshortener.shortener.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Map;

/**
 * Central exception → HTTP mapping. Error body is ALWAYS
 * {@code {"error": "..."}} (conventions). Domain exceptions stay thin
 * RuntimeExceptions; status semantics live here in one place.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler({ValidationException.class, HttpMessageNotReadableException.class,
            MethodArgumentNotValidException.class, MissingRequestHeaderException.class})
    public ResponseEntity<Map<String, String>> badRequest(Exception e) {
        String message = e instanceof ValidationException ? e.getMessage() : "malformed request";
        return body(HttpStatus.BAD_REQUEST, message);
    }

    @ExceptionHandler({NotFoundException.class, NoResourceFoundException.class})
    public ResponseEntity<Map<String, String>> notFound(Exception e) {
        return body(HttpStatus.NOT_FOUND, "not found");
    }

    @ExceptionHandler(AliasConflictException.class)
    public ResponseEntity<Map<String, String>> conflict(AliasConflictException e) {
        return body(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<Map<String, String>> emailTaken(EmailAlreadyExistsException e) {
        return body(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler({InvalidCredentialsException.class, InvalidTokenException.class})
    public ResponseEntity<Map<String, String>> unauthorized(RuntimeException e) {
        return body(HttpStatus.UNAUTHORIZED, e.getMessage());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String, String>> methodNotAllowed(HttpRequestMethodNotSupportedException e) {
        return body(HttpStatus.METHOD_NOT_ALLOWED, "method not allowed");
    }

    /** 503, never 404: "we don't know" must not look like "it doesn't exist". */
    @ExceptionHandler({InfraUnavailableException.class, DataAccessException.class, io.portfolio.urlshortener.sharding.ShardUnavailableException.class})
    public ResponseEntity<Map<String, String>> unavailable(Exception e) {
        log.warn("infrastructure unavailable: {}", e.getMessage());
        return body(HttpStatus.SERVICE_UNAVAILABLE, "service temporarily unavailable");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> internal(Exception e) {
        log.error("unhandled exception", e);
        return body(HttpStatus.INTERNAL_SERVER_ERROR, "internal error");
    }

    private static ResponseEntity<Map<String, String>> body(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("error", message));
    }
}
