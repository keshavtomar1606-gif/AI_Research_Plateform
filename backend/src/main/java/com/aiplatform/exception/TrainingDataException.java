package com.aiplatform.exception;

import org.springframework.http.HttpStatus;

/** The uploaded data cannot be used (bad CSV, non-numeric column, too few rows...). The message is shown to the user. */
public class TrainingDataException extends ResearchAiException {

    public TrainingDataException(String message) {
        super(message);
    }

    @Override
    public HttpStatus status() {
        return HttpStatus.BAD_REQUEST;
    }
}
