package com.aiplatform.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.Map;

public record PredictRequest(@NotEmpty(message = "Provide a value for every feature") Map<String, Double> features) {
}
