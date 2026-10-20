package com.aiplatform.config;

import com.aiplatform.exception.ResearchAiException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Turns every error into a small JSON body: {"error": "message"}.
 * Internal details (SQL, stack traces) are only written to the server log, never sent to the browser.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static ResponseEntity<Map<String, Object>> body(HttpStatus status, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", message);
        return ResponseEntity.status(status).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, Object>> validation(MethodArgumentNotValidException e) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (FieldError fe : e.getBindingResult().getFieldErrors()) fields.putIfAbsent(fe.getField(), fe.getDefaultMessage());
        String first = fields.isEmpty() ? "Invalid request." : fields.values().iterator().next();
        ResponseEntity<Map<String, Object>> response = body(HttpStatus.BAD_REQUEST, first);
        response.getBody().put("fields", fields);
        return response;
    }

    /** Validation of method parameters, e.g. each item of a list in a request body. */
    @ExceptionHandler({org.springframework.web.method.annotation.HandlerMethodValidationException.class,
            jakarta.validation.ConstraintViolationException.class})
    ResponseEntity<Map<String, Object>> parameterValidation(Exception e) {
        return body(HttpStatus.BAD_REQUEST, "Some fields are missing or invalid. Check the values and try again.");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<Map<String, Object>> unreadable(HttpMessageNotReadableException e) {
        return body(HttpStatus.BAD_REQUEST, "Request body is missing or is not valid JSON.");
    }

    @ExceptionHandler({MissingServletRequestPartException.class, MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class})
    ResponseEntity<Map<String, Object>> badParameter(Exception e) {
        return body(HttpStatus.BAD_REQUEST, "A required field or file is missing or has the wrong type.");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<Map<String, Object>> tooLarge(MaxUploadSizeExceededException e) {
        return body(HttpStatus.PAYLOAD_TOO_LARGE, "File is too large (maximum 2 MB).");
    }

    /** One handler for all of the application's own exceptions: each subclass supplies its own status. */
    @ExceptionHandler(ResearchAiException.class)
    ResponseEntity<Map<String, Object>> application(ResearchAiException e) {
        if (e.isServerError()) log.error("Request failed", e);
        return body(e.status(), e.getMessage());
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<Map<String, Object>> status(ResponseStatusException e) {
        HttpStatus status = HttpStatus.valueOf(e.getStatusCode().value());
        return body(status, e.getReason() != null ? e.getReason() : status.getReasonPhrase());
    }

    @ExceptionHandler({DataAccessException.class, CannotCreateTransactionException.class})
    ResponseEntity<Map<String, Object>> database(Exception e) {
        log.error("Database error", e);
        return body(HttpStatus.SERVICE_UNAVAILABLE, "Database error. Check that MySQL is running and try again.");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<Map<String, Object>> notFound(NoResourceFoundException e) {
        return body(HttpStatus.NOT_FOUND, "Not found.");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<Map<String, Object>> method(HttpRequestMethodNotSupportedException e) {
        return body(HttpStatus.METHOD_NOT_ALLOWED, "Method not allowed.");
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Map<String, Object>> unexpected(Exception e) {
        log.error("Unexpected error", e);
        return body(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected server error.");
    }
}
