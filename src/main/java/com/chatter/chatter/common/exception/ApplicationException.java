package com.chatter.chatter.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Base for the domain's own exceptions, each carrying the HTTP status it should
 * surface as.
 *
 * <p>Lets {@code GlobalExceptionHandler} map every domain failure with one
 * handler instead of a branch per type, while keeping the status decision beside
 * the exception that knows what it means — a missing chat is a 404, a
 * non-participant a 403.
 */
public abstract class ApplicationException extends RuntimeException {

    private final HttpStatus status;

    /**
     * @param status the response status this failure should produce
     * @param message the client-facing message; keep it free of anything an
     *        unauthenticated caller should not learn
     */
    protected ApplicationException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    /** The status to respond with; read by {@code GlobalExceptionHandler}. */
    public HttpStatus getStatus() {
        return status;
    }
}
