package com.aiplatform.dto;

import com.aiplatform.model.Experiment;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ExperimentRequest(
        @NotBlank(message = "Experiment name is required") @Size(max = 200) String name,
        @NotBlank(message = "Model name is required") @Size(max = 200) String model,
        @Size(max = 255) String metric,
        @Pattern(regexp = "Planned|Running|Completed|Failed", message = "Status must be Planned, Running, Completed or Failed") String status) {

    public Experiment toExperiment() {
        return new Experiment(null, name.trim(), model.trim(), metric == null ? "" : metric.trim(),
                status == null ? "Planned" : status, null);
    }
}
