package com.aiplatform.model;

import java.time.Instant;

/**
 * A manually logged experiment note. Stored with plain JDBC (see JdbcExperimentDao), not JPA,
 * to show the low-level database API: Connection, PreparedStatement and ResultSet.
 */
public record Experiment(Long id, String name, String model, String metric, String status, Instant createdAt) {

    /** Copy of this experiment with a database-generated id and timestamp. */
    public Experiment withId(long newId, Instant created) {
        return new Experiment(newId, name, model, metric, status, created);
    }
}
