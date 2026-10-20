package com.aiplatform.dao;

import com.aiplatform.exception.DaoException;
import com.aiplatform.model.Experiment;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.stereotype.Repository;

/**
 * ExperimentDao implemented with plain JDBC.
 *
 * Every method follows the same steps:
 *   1. borrow a Connection from the pool (DataSource),
 *   2. run SQL through a PreparedStatement (the "?" placeholders prevent SQL injection),
 *   3. read rows from the ResultSet,
 *   4. close everything automatically with try-with-resources.
 * SQLException (a checked exception) is wrapped in DaoException so callers stay simple.
 */
@Repository
public class JdbcExperimentDao implements ExperimentDao {

    private static final String CREATE_TABLE = """
            CREATE TABLE IF NOT EXISTS experiment (
                id         BIGINT       NOT NULL AUTO_INCREMENT,
                name       VARCHAR(200) NOT NULL,
                model      VARCHAR(200) NOT NULL,
                metric     VARCHAR(255),
                status     VARCHAR(30)  NOT NULL,
                created_at DATETIME(6)  NOT NULL,
                PRIMARY KEY (id)
            )""";
    private static final String SELECT_ALL = "SELECT id, name, model, metric, status, created_at FROM experiment ORDER BY id DESC";
    private static final String SELECT_ONE = "SELECT id, name, model, metric, status, created_at FROM experiment WHERE id = ?";
    private static final String INSERT = "INSERT INTO experiment (name, model, metric, status, created_at) VALUES (?, ?, ?, ?, ?)";
    private static final String UPDATE = "UPDATE experiment SET name = ?, model = ?, metric = ?, status = ? WHERE id = ?";
    private static final String DELETE = "DELETE FROM experiment WHERE id = ?";
    private static final String COUNT = "SELECT COUNT(*) FROM experiment";

    private final DataSource dataSource;

    public JdbcExperimentDao(DataSource dataSource) {
        this.dataSource = dataSource;
        createTableIfMissing();
    }

    /** The experiment table is not managed by Hibernate, so it is created here with a JDBC Statement. */
    private void createTableIfMissing() {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(CREATE_TABLE);
        } catch (SQLException e) {
            throw new DaoException("create the experiment table", e);
        }
    }

    @Override
    public List<Experiment> findAll() {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_ALL);
             ResultSet rows = statement.executeQuery()) {
            List<Experiment> result = new ArrayList<>();
            while (rows.next()) result.add(map(rows));
            return result;
        } catch (SQLException e) {
            throw new DaoException("load experiments", e);
        }
    }

    @Override
    public Optional<Experiment> findById(long id) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_ONE)) {
            statement.setLong(1, id);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(map(rows)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DaoException("load an experiment", e);
        }
    }

    @Override
    public Experiment insert(Experiment experiment) {
        try (Connection connection = dataSource.getConnection()) {
            return insert(connection, experiment);
        } catch (SQLException e) {
            throw new DaoException("save the experiment", e);
        }
    }

    /**
     * Transaction example: auto-commit is switched off so all inserts share one transaction.
     * If any insert fails, rollback() undoes the ones that already ran, so the table never
     * contains half of a batch.
     */
    @Override
    public List<Experiment> insertAll(List<Experiment> experiments) {
        try (Connection connection = dataSource.getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                List<Experiment> saved = new ArrayList<>();
                for (Experiment experiment : experiments) saved.add(insert(connection, experiment));
                connection.commit();
                return saved;
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        } catch (SQLException e) {
            throw new DaoException("save the experiments (nothing was saved)", e);
        }
    }

    @Override
    public boolean update(long id, Experiment experiment) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(UPDATE)) {
            statement.setString(1, experiment.name());
            statement.setString(2, experiment.model());
            statement.setString(3, experiment.metric());
            statement.setString(4, experiment.status());
            statement.setLong(5, id);
            return statement.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new DaoException("update the experiment", e);
        }
    }

    @Override
    public boolean delete(long id) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(DELETE)) {
            statement.setLong(1, id);
            return statement.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new DaoException("delete the experiment", e);
        }
    }

    @Override
    public int count() {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(COUNT);
             ResultSet rows = statement.executeQuery()) {
            rows.next();
            return rows.getInt(1);
        } catch (SQLException e) {
            throw new DaoException("count experiments", e);
        }
    }

    /** Inserts using an existing connection (so it can be part of a transaction) and reads back the generated id. */
    private static Experiment insert(Connection connection, Experiment experiment) throws SQLException {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        try (PreparedStatement statement = connection.prepareStatement(INSERT, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, experiment.name());
            statement.setString(2, experiment.model());
            statement.setString(3, experiment.metric());
            statement.setString(4, experiment.status());
            statement.setTimestamp(5, Timestamp.from(now));
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) throw new SQLException("No id was generated for the new experiment.");
                return experiment.withId(keys.getLong(1), now);
            }
        }
    }

    /** Converts the current ResultSet row into an Experiment object. */
    private static Experiment map(ResultSet row) throws SQLException {
        Timestamp created = row.getTimestamp("created_at");
        return new Experiment(row.getLong("id"), row.getString("name"), row.getString("model"),
                row.getString("metric"), row.getString("status"), created == null ? null : created.toInstant());
    }
}
