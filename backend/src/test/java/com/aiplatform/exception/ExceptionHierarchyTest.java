package com.aiplatform.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ExceptionHierarchyTest {

    @Test
    void eachSubclassChoosesItsOwnStatus() {
        List<ResearchAiException> errors = List.of(
                new TrainingDataException("bad csv"),
                new NotFoundException("Experiment", 7),
                new DaoException("load experiments", new SQLException("connection refused")));
        // Same method call, different behaviour per class (polymorphism).
        assertEquals(List.of(HttpStatus.BAD_REQUEST, HttpStatus.NOT_FOUND, HttpStatus.SERVICE_UNAVAILABLE),
                errors.stream().map(ResearchAiException::status).toList());
        assertFalse(errors.get(0).isServerError());
        assertTrue(errors.get(2).isServerError());
    }

    @Test
    void daoExceptionHidesSqlDetailsButKeepsTheCause() {
        DaoException e = new DaoException("load experiments", new SQLException("Access denied for user 'root'"));
        assertFalse(e.getMessage().contains("root"));
        assertTrue(e.getCause() instanceof SQLException);
        assertEquals("Experiment 7 not found.", new NotFoundException("Experiment", 7).getMessage());
    }
}
