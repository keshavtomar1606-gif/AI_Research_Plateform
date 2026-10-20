package com.aiplatform.dto;

import com.aiplatform.model.TrainingJob;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public record TrainingJobResponse(
        Long id, String jobType, String model, String dataset, String parameters, String status, Integer progress,
        Long datasetId, String targetColumn, List<String> featureColumns, Double testRatio, Long randomSeed,
        Integer trainRows, Integer testRows, Integer droppedRows,
        Double mae, Double rmse, Double r2, Double trainR2, Double baselineMae,
        Double intercept, Map<String, Double> coefficients, String errorMessage,
        Instant createdAt, Instant startedAt, Instant finishedAt, Long durationMs) {

    private static final ObjectMapper JSON = new ObjectMapper();

    public static TrainingJobResponse of(TrainingJob j) {
        Double intercept = null;
        Map<String, Double> coefficients = null;
        if (j.getCoefficientsJson() != null) {
            try {
                Map<String, Object> parsed = JSON.readValue(j.getCoefficientsJson(), new TypeReference<>() {});
                intercept = ((Number) parsed.get("intercept")).doubleValue();
                coefficients = JSON.convertValue(parsed.get("coefficients"), new TypeReference<>() {});
            } catch (Exception ignored) {
                // A corrupted value simply shows no coefficients.
            }
        }
        List<String> features = j.getFeatureColumns() == null || j.getFeatureColumns().isBlank()
                ? List.of() : Arrays.asList(j.getFeatureColumns().split("\t"));
        Long duration = j.getStartedAt() != null && j.getFinishedAt() != null
                ? Duration.between(j.getStartedAt(), j.getFinishedAt()).toMillis() : null;
        return new TrainingJobResponse(j.getId(), j.isMlJob() ? TrainingJob.TYPE_ML : TrainingJob.TYPE_RECORD,
                j.getModel(), j.getDataset(), j.getParameters(), j.getStatus(), j.getProgress(),
                j.getDatasetId(), j.getTargetColumn(), features, j.getTestRatio(), j.getRandomSeed(),
                j.getTrainRows(), j.getTestRows(), j.getDroppedRows(),
                j.getMae(), j.getRmse(), j.getR2(), j.getTrainR2(), j.getBaselineMae(),
                intercept, coefficients, j.getErrorMessage(), j.getCreatedAt(), j.getStartedAt(), j.getFinishedAt(), duration);
    }
}
