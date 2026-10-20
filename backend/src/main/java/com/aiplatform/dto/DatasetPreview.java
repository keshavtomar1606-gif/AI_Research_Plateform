package com.aiplatform.dto;

import java.util.List;
import java.util.Map;

public record DatasetPreview(List<String> columns, List<String> numericColumns, Map<String, Integer> missingValues,
                             int totalRows, List<List<String>> rows) {
}
