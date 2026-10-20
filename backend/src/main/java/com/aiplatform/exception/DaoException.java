package com.aiplatform.exception;

import org.springframework.http.HttpStatus;

/**
 * A JDBC operation failed. Wraps the checked SQLException so callers don't need try/catch everywhere.
 * The message is generic; the original SQLException (the cause) is only written to the server log.
 */
public class DaoException extends ResearchAiException {

    public DaoException(String operation, Throwable cause) {
        super("Database error while trying to " + operation + ". Check that MySQL is running and try again.", cause);
    }

    @Override
    public HttpStatus status() {
        return HttpStatus.SERVICE_UNAVAILABLE;
    }
}
