package com.aiplatform.dao;

import com.aiplatform.model.Experiment;
import java.util.List;
import java.util.Optional;

/**
 * Database operations for experiment notes. Code that uses experiments depends only on this
 * interface, so the storage can be swapped (e.g. a different database) without changing callers.
 */
public interface ExperimentDao {

    List<Experiment> findAll();

    Optional<Experiment> findById(long id);

    /** Inserts and returns the saved experiment with its generated id. */
    Experiment insert(Experiment experiment);

    /** Inserts all experiments in one transaction: either all are saved or none are. */
    List<Experiment> insertAll(List<Experiment> experiments);

    /** Returns false if no experiment has this id. */
    boolean update(long id, Experiment experiment);

    /** Returns false if no experiment has this id. */
    boolean delete(long id);

    int count();
}
