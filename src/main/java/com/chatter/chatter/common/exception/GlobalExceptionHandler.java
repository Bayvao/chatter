package com.chatter.chatter.common.exception;

import java.time.Instant;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Turns exceptions into JSON error responses, application-wide.
 *
 * <p>Keeps controllers free of try/catch: they call services and let failures
 * propagate. Without this, an uncaught domain exception would surface as a bare
 * 500 with a stack trace.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** The one error shape every failed request returns. */
    public record ErrorResponse(Instant timestamp, int status, String error, String message) {
    }

    /**
     * Handles the domain's own exceptions, each of which names its own status.
     *
     * <p>Covers everything extending {@code ApplicationException}: unknown user
     * or chat as 404, non-participant as 403, duplicate username or contact as
     * 409.
     */
    @ExceptionHandler(ApplicationException.class)
    public ResponseEntity<ErrorResponse> handleApplication(ApplicationException ex) {
        return build(ex.getStatus(), ex.getMessage());
    }

    /**
     * Turns bean-validation failures into a 400 naming the offending fields.
     *
     * <p>Triggered by {@code @Valid} on request bodies. Field errors are joined
     * into one message so a client sees everything wrong at once rather than
     * fixing one field per round trip.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + " " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return build(HttpStatus.BAD_REQUEST, message.isBlank() ? "Validation failed" : message);
    }

    /**
     * Turns a failed login into a 401.
     *
     * <p>The message is deliberately the same whether the username is unknown or
     * the password is wrong: distinguishing them lets an attacker enumerate
     * accounts.
     */
    @ExceptionHandler({BadCredentialsException.class, AuthenticationException.class})
    public ResponseEntity<ErrorResponse> handleAuthentication(AuthenticationException ex) {
        return build(HttpStatus.UNAUTHORIZED, "Invalid username or password");
    }

    /**
     * Maps argument-level rejections to 400 rather than 500.
     *
     * <p>Catches the guards services raise for nonsensical input — opening a
     * chat with yourself, adding yourself as a contact — which are client
     * mistakes, not server faults.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    /** Assembles the response body every handler above returns. */
    private ResponseEntity<ErrorResponse> build(HttpStatus status, String message) {
        return ResponseEntity.status(status)
                .body(new ErrorResponse(Instant.now(), status.value(), status.getReasonPhrase(), message));
    }
}
