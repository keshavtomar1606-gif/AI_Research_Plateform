package com.aiplatform.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A note about a job run elsewhere. Saving it does not train anything. */
public record TrainingRecordRequest(
        @NotBlank(message = "Model name is required") @Size(max = 200) String model,
        @NotBlank(message = "Dataset name is required") @Size(max = 200) String dataset,
        @Size(max = 255) String parameters) {
}
