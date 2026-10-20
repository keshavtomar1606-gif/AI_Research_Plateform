package com.aiplatform.controller;

import com.aiplatform.dto.PredictRequest;
import com.aiplatform.dto.TrainingJobResponse;
import com.aiplatform.dto.TrainingRecordRequest;
import com.aiplatform.dto.TrainingRunRequest;
import com.aiplatform.ml.TrainingService;
import com.aiplatform.model.TrainingJob;
import com.aiplatform.repository.TrainingJobRepository;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/training")
public class TrainingJobController {

    private final TrainingJobRepository repository;
    private final TrainingService trainingService;

    public TrainingJobController(TrainingJobRepository repository, TrainingService trainingService) {
        this.repository = repository;
        this.trainingService = trainingService;
    }

    @GetMapping
    public List<TrainingJobResponse> all() {
        return repository.findAllByOrderByIdDesc().stream().map(TrainingJobResponse::of).toList();
    }

    @GetMapping("/{id}")
    public TrainingJobResponse one(@PathVariable Long id) {
        return TrainingJobResponse.of(find(id));
    }

    /** Starts a real linear-regression training run. Poll GET /api/training/{id} for its status. */
    @PostMapping("/run")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public TrainingJobResponse run(@Valid @RequestBody TrainingRunRequest request) {
        return TrainingJobResponse.of(trainingService.start(request));
    }

    /** Uses a completed run's coefficients to predict the target for new feature values. */
    @PostMapping("/{id}/predict")
    public Map<String, Object> predict(@PathVariable Long id, @Valid @RequestBody PredictRequest request) {
        TrainingJob job = find(id);
        double prediction = trainingService.predict(job, request.features());
        return Map.of("target", job.getTargetColumn(), "prediction", prediction);
    }

    /** Saves a note about a job run elsewhere. Nothing is trained, so the status is "Record only". */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TrainingJobResponse create(@Valid @RequestBody TrainingRecordRequest input) {
        TrainingJob job = new TrainingJob();
        job.setJobType(TrainingJob.TYPE_RECORD);
        job.setModel(input.model().trim());
        job.setDataset(input.dataset().trim());
        job.setParameters(input.parameters());
        job.setStatus("Record only");
        job.setProgress(0);
        return TrainingJobResponse.of(repository.save(job));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        TrainingJob job = find(id);
        if (job.isMlJob() && (TrainingJob.QUEUED.equals(job.getStatus()) || TrainingJob.RUNNING.equals(job.getStatus()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This job is still running. Wait for it to finish before deleting it.");
        }
        repository.delete(job);
    }

    private TrainingJob find(Long id) {
        return repository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Training job not found."));
    }
}
