package com.aiplatform.exception;

import org.springframework.http.HttpStatus;

/** A record with the requested id does not exist. */
public class NotFoundException extends ResearchAiException {

    public NotFoundException(String what, long id) {
        super(what + " " + id + " not found.");
    }

    @Override
    public HttpStatus status() {
        return HttpStatus.NOT_FOUND;
    }
}
