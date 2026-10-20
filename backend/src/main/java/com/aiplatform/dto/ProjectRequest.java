package com.aiplatform.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ProjectRequest(
        @NotBlank(message = "Project title is required") @Size(max = 200) String title,
        @Size(max = 2000) String description,
        @Size(max = 255) String team,
        @Pattern(regexp = "Active|Planning|Completed|On hold", message = "Status must be Active, Planning, Completed or On hold") String status) {
}
