package com.apps.ecommerce.exception;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import javax.naming.AuthenticationException;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // catches validation errors from @Valid
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponseDto handleValidation(
            MethodArgumentNotValidException ex) {

        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(err -> errors.put(err.getField(), err.getDefaultMessage()));
        return new ErrorResponseDto(
                400, "Not valid", ex.getMessage(), LocalDateTime.now(), errors);
    }

    // catches failed logins (bad password, unknown email, disabled account).
    // message stays generic on purpose — a specific one tells an attacker
    // whether the email exists
    @ExceptionHandler(AuthenticationException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ErrorResponseDto handleAuthentication(AuthenticationException ex) {
        return new ErrorResponseDto(
                401, "invalid email or password", ex.getMessage(), LocalDateTime.now(), null);
    }

    // catches RuntimeException (your custom exceptions go here)
    @ExceptionHandler(RuntimeException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorResponseDto handleRuntime(RuntimeException ex) {
        return new ErrorResponseDto(500, "server error", ex.getMessage(), LocalDateTime.now(), null);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponseDto handleNotFound(ResourceNotFoundException ex) {
        return new ErrorResponseDto(404, "Not found", ex.getMessage(), LocalDateTime.now(), null);
    }

    @ExceptionHandler(DuplicateResourceException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponseDto handleDuplicate(DuplicateResourceException ex) {
        return new ErrorResponseDto(409, "email already registered", ex.getMessage(), LocalDateTime.now(), null);
    }
}
