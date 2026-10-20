package com.aiplatform.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ChatMessage(
        @NotBlank @Pattern(regexp = "user|assistant", message = "role must be user or assistant") String role,
        @NotBlank @Size(max = 8000) String content) {
}
