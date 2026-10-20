package com.aiplatform.dto;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

/** Dataset as sent to the frontend. The raw CSV content is deliberately left out. */
public record DatasetSummary(Long id, String name, String description, String owner, String fileName, String status,
                             List<String> columns, Integer rowCount, Long sizeBytes, boolean hasData, Instant createdAt) {

    public DatasetSummary(Long id, String name, String description, String owner, String fileName, String status,
                          String columnNames, Integer rowCount, Long sizeBytes, Instant createdAt) {
        this(id, name, description, owner, fileName, status, splitColumns(columnNames), rowCount, sizeBytes,
             columnNames != null && !columnNames.isBlank(), createdAt);
    }

    public static DatasetSummary of(com.aiplatform.model.Dataset d) {
        return new DatasetSummary(d.getId(), d.getName(), d.getDescription(), d.getOwner(), d.getFileName(), d.getStatus(),
                                  d.getColumnNames(), d.getRowCount(), d.getSizeBytes(), d.getCreatedAt());
    }

    private static List<String> splitColumns(String columnNames) {
        if (columnNames == null || columnNames.isBlank()) return List.of();
        return Arrays.asList(columnNames.split("\t"));
    }
}
