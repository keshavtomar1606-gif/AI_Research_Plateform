package com.aiplatform.controller;

import com.aiplatform.dao.ExperimentDao;
import com.aiplatform.dto.ExperimentRequest;
import com.aiplatform.exception.NotFoundException;
import com.aiplatform.model.Experiment;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/** Experiment notes, stored through the plain-JDBC ExperimentDao. */
@RestController
@RequestMapping("/api/experiments")
public class ExperimentController {
    private final ExperimentDao dao;

    public ExperimentController(ExperimentDao dao) {
        this.dao = dao;
    }

    @GetMapping
    public List<Experiment> all() {
        return dao.findAll();
    }

    @GetMapping("/{id}")
    public Experiment one(@PathVariable long id) {
        return dao.findById(id).orElseThrow(() -> new NotFoundException("Experiment", id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Experiment create(@Valid @RequestBody ExperimentRequest request) {
        return dao.insert(request.toExperiment());
    }

    /** Saves several experiments in one database transaction (all or nothing). */
    @PostMapping("/batch")
    @ResponseStatus(HttpStatus.CREATED)
    public List<Experiment> createAll(@RequestBody @NotEmpty @Size(max = 100) List<@Valid ExperimentRequest> requests) {
        return dao.insertAll(requests.stream().map(ExperimentRequest::toExperiment).toList());
    }

    @PutMapping("/{id}")
    public Experiment update(@PathVariable long id, @Valid @RequestBody ExperimentRequest request) {
        if (!dao.update(id, request.toExperiment())) throw new NotFoundException("Experiment", id);
        return one(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        if (!dao.delete(id)) throw new NotFoundException("Experiment", id);
    }
}
