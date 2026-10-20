package com.aiplatform.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record DatasetRequest(
        @NotBlank(message = "Dataset name is required") @Size(max = 200) String name,
        @Size(max = 2000) String description,
        @Size(max = 120) String owner,
        @Size(max = 255) String fileName,
        @Pattern(regexp = "Ready|Metadata only|Archived", message = "Status must be Ready, Metadata only or Archived") String status) {
}
