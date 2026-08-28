package com.apps.ecommerce.exception;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.AuthenticationException;

import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import lombok.extern.slf4j.Slf4j;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleValidation(
            MethodArgumentNotValidException ex) {

        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(err -> errors.put(err.getField(), err.getDefaultMessage()));
        return new ErrorResponse(
                400, "Not valid", ex.getMessage(), LocalDateTime.now(), errors);
    }

    /**
     * A body Jackson cannot parse — malformed JSON, or a value that does not fit
     * the target type, such as "role": "superuser" against the Role enum. Without
     * this, the catch-all Exception handler below claims it as a 500, even though
     * the request is the client's mistake.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleUnreadableBody(HttpMessageNotReadableException ex) {
        return new ErrorResponse(
                400, "Malformed request body", ex.getMostSpecificCause().getMessage(), LocalDateTime.now(), null);
    }

    /**
     * More specific than the AuthenticationException handler below, so Spring picks
     * this one for a disabled account. Without it, an unverified user is told
     * "invalid email or password" and goes looking for a typo in a password that
     * was correct all along.
     *
     * The status stays 401 rather than 403 on purpose. Spring checks the enabled
     * flag *before* the password, so a 403 here would confirm an account exists
     * even to someone guessing with the wrong password.
     */
    @ExceptionHandler(DisabledException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ErrorResponse handleDisabled(DisabledException ex) {
        return new ErrorResponse(
                401, "Account not verified",
                "Check your email for the activation link, or request a new one at /api/v1/auth/resend",
                LocalDateTime.now(), null);
    }

    @ExceptionHandler(AuthenticationException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ErrorResponse handleAuthentication(AuthenticationException ex) {
        // Deliberately vague, and deliberately not echoing ex.getMessage(): the
        // caller should not learn whether it was the address or the password that
        // was wrong.
        return new ErrorResponse(
                401, "invalid email or password", ex.getMessage(), LocalDateTime.now(), null);
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorResponse handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex);
        return new ErrorResponse(500, "Something went wrong", ex.getMessage(), LocalDateTime.now(), null);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleNotFound(ResourceNotFoundException ex) {
        return new ErrorResponse(404, "Not found", ex.getMessage(), LocalDateTime.now(), null);
    }

    @ExceptionHandler(DuplicateResourceException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleDuplicate(DuplicateResourceException ex) {
        return new ErrorResponse(409, "email already registered", ex.getMessage(), LocalDateTime.now(), null);
    }

    @ExceptionHandler(InvalidTokenException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleInvalidToken(InvalidTokenException ex) {
        return new ErrorResponse(400, "Invalid token", ex.getMessage(), LocalDateTime.now(), null);
    }

    @ExceptionHandler(InsufficientStockException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse noStock(InsufficientStockException ex) {
        return new ErrorResponse(409, "no stock", ex.getMessage(), LocalDateTime.now(), null);
    }

    @ExceptionHandler(TooManyRequestsException.class)
    @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
    public ErrorResponse handleTooManyRequests(TooManyRequestsException ex) {
        return new ErrorResponse(429, "Too many requests", ex.getMessage(), LocalDateTime.now(), null);
    }
}
