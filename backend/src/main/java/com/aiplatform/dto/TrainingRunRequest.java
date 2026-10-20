package com.aiplatform.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record TrainingRunRequest(
        @NotNull(message = "Choose a dataset") Long datasetId,
        @NotBlank(message = "Choose a target column") String targetColumn,
        @NotEmpty(message = "Choose at least one feature column") @Size(max = 30) List<@NotBlank String> featureColumns,
        @DecimalMin(value = "0.1", message = "Test split must be between 0.1 and 0.5")
        @DecimalMax(value = "0.5", message = "Test split must be between 0.1 and 0.5") Double testRatio,
        Long randomSeed) {
}
