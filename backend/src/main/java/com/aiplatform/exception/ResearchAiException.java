package com.aiplatform.exception;

import org.springframework.http.HttpStatus;

/**
 * Base class for the application's own exceptions.
 *
 * Each subclass decides which HTTP status the API returns (polymorphism): GlobalExceptionHandler
 * only calls {@link #status()} and never needs to know the concrete type.
 */
public abstract class ResearchAiException extends RuntimeException {

    protected ResearchAiException(String message) {
        super(message);
    }

    protected ResearchAiException(String message, Throwable cause) {
        super(message, cause);
    }

    /** HTTP status sent to the browser for this kind of error. */
    public abstract HttpStatus status();

    /** Whether the problem is on the server side and should be written to the log. */
    public boolean isServerError() {
        return status().is5xxServerError();
    }
}
