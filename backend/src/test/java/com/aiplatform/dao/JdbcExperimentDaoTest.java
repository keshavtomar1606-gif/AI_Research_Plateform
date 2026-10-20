package com.aiplatform.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aiplatform.exception.DaoException;
import com.aiplatform.model.Experiment;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** Tests the plain-JDBC DAO against an in-memory H2 database. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:daotest;MODE=MySQL;DB_CLOSE_DELAY=-1")
class JdbcExperimentDaoTest {

    @Autowired ExperimentDao dao;

    private static Experiment note(String name) {
        return new Experiment(null, name, "Linear Regression", "R² 0.95", "Completed", null);
    }

    @Test
    void insertFindUpdateDelete() {
        Experiment saved = dao.insert(note("Baseline v1"));
        assertNotNull(saved.id());
        assertNotNull(saved.createdAt());

        Experiment loaded = dao.findById(saved.id()).orElseThrow();
        assertEquals("Baseline v1", loaded.name());
        assertEquals("R² 0.95", loaded.metric());   // unicode survives the round trip
        assertTrue(dao.findAll().stream().anyMatch(e -> e.id().equals(saved.id())));

        assertTrue(dao.update(saved.id(), new Experiment(null, "Baseline v2", "Ridge", "R² 0.96", "Running", null)));
        assertEquals("Ridge", dao.findById(saved.id()).orElseThrow().model());

        assertTrue(dao.delete(saved.id()));
        assertTrue(dao.findById(saved.id()).isEmpty());
        assertFalse(dao.delete(saved.id()));
        assertFalse(dao.update(saved.id(), note("x")));
    }

    @Test
    void sqlInjectionAttemptIsStoredAsPlainText() {
        String attack = "x'); DROP TABLE experiment; --";
        Experiment saved = dao.insert(note(attack));
        assertEquals(attack, dao.findById(saved.id()).orElseThrow().name());
        assertTrue(dao.count() >= 1);   // table still exists
    }

    @Test
    void batchInsertIsAllOrNothing() {
        int before = dao.count();
        List<Experiment> saved = dao.insertAll(List.of(note("Run A"), note("Run B")));
        assertEquals(2, saved.size());
        assertEquals(before + 2, dao.count());

        // The second row is too long for VARCHAR(200), so the database rejects it and the first insert is rolled back.
        int beforeFailure = dao.count();
        assertThrows(DaoException.class, () -> dao.insertAll(List.of(note("Valid row"), note("x".repeat(300)))));
        assertEquals(beforeFailure, dao.count());
    }
}
